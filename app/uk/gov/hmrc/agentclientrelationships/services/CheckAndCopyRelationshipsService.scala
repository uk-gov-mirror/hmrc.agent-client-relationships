/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentclientrelationships.services

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.Logging
import play.api.mvc.Request
import uk.gov.hmrc.agentclientrelationships.audit.{AuditData, AuditService}
import uk.gov.hmrc.agentclientrelationships.config.AppConfig
import uk.gov.hmrc.agentclientrelationships.connectors._
import uk.gov.hmrc.agentclientrelationships.controllers.fluentSyntax.returnValue
import uk.gov.hmrc.agentclientrelationships.repository.RelationshipReference.{SaRef, VatRef}
import uk.gov.hmrc.agentclientrelationships.repository.{SyncStatus => _, _}
import uk.gov.hmrc.agentclientrelationships.support.{Monitoring, RelationshipNotFound}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId, Vrn}
import uk.gov.hmrc.domain.{AgentCode, Nino, SaAgentReference, TaxIdentifier}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

sealed trait CheckAndCopyResult {
  val grantAccess: Boolean
}

case object CheckAndCopyNotImplemented extends CheckAndCopyResult {
  override val grantAccess = false
}

case object AlreadyCopiedDidNotCheck extends CheckAndCopyResult {
  override val grantAccess = false
}

case object FoundAndCopied extends CheckAndCopyResult {
  override val grantAccess = true
}

case object FoundButLockedCouldNotCopy extends CheckAndCopyResult {
  override val grantAccess = true
}

case object FoundAndFailedToCopy extends CheckAndCopyResult {
  override val grantAccess = true
}

case object NotFound extends CheckAndCopyResult {
  override val grantAccess = false
}

case object CopyRelationshipNotEnabled extends CheckAndCopyResult {
  override val grantAccess = false
}

@Singleton
class CheckAndCopyRelationshipsService @Inject()(
  es: EnrolmentStoreProxyConnector,
  des: DesConnector,
  mapping: MappingConnector,
  ugs: UsersGroupsSearchConnector,
  relationshipCopyRepository: RelationshipCopyRecordRepository,
  createRelationshipsService: CreateRelationshipsService,
  val auditService: AuditService,
  val metrics: Metrics)(implicit val appConfig: AppConfig)
    extends Monitoring
    with Logging {

  val copyMtdItRelationshipFlag = appConfig.copyMtdItRelationshipFlag
  val copyMtdVatRelationshipFlag = appConfig.copyMtdVatRelationshipFlag

  def checkForOldRelationshipAndCopy(arn: Arn, identifier: TaxIdentifier)(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier,
    request: Request[Any],
    auditData: AuditData): Future[CheckAndCopyResult] = {

    def ifEnabled(copyRelationshipFlag: Boolean)(body: => Future[CheckAndCopyResult]): Future[CheckAndCopyResult] =
      if (copyRelationshipFlag) {
        body
      } else {
        returnValue(CopyRelationshipNotEnabled)
      }

    identifier match {
      case mtdItId @ MtdItId(_) =>
        ifEnabled(copyMtdItRelationshipFlag)(checkCesaForOldRelationshipAndCopyForMtdIt(arn, mtdItId))
      case vrn @ Vrn(_) =>
        ifEnabled(copyMtdVatRelationshipFlag)(checkESForOldRelationshipAndCopyForMtdVat(arn, vrn))

      case _ => Future.successful(CheckAndCopyNotImplemented)
    }
  }

  private def checkCesaForOldRelationshipAndCopyForMtdIt(arn: Arn, mtdItId: MtdItId)(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier,
    request: Request[Any],
    auditData: AuditData): Future[CheckAndCopyResult] = {

    auditData.set("Journey", "CopyExistingCESARelationship")
    auditData.set("service", "mtd-it")
    auditData.set("clientId", mtdItId)
    auditData.set("clientIdType", "mtditid")

    relationshipCopyRepository.findBy(arn, mtdItId).flatMap {
      case Some(relationshipCopyRecord) if !relationshipCopyRecord.actionRequired =>
        logger.warn(s"Relationship has been already been found in CESA and we have already attempted to copy to MTD")
        Future successful AlreadyCopiedDidNotCheck
      case maybeRelationshipCopyRecord @ _ =>
        for {
          nino       <- des.getNinoFor(mtdItId)
          references <- lookupCesaForOldRelationship(arn, nino)
          result <- if (references.nonEmpty)
                     findOrCreateRelationshipCopyRecordAndCopy(
                       references.map(SaRef.apply),
                       maybeRelationshipCopyRecord,
                       arn,
                       mtdItId)
                       .map {
                         case Some(_) =>
                           auditService.sendCreateRelationshipAuditEvent
                           mark("Count-CopyRelationship-ITSA-FoundAndCopied")
                           FoundAndCopied
                         case None =>
                           auditService.sendCreateRelationshipAuditEvent
                           mark("Count-CopyRelationship-ITSA-FoundButLockedCouldNotCopy")
                           logger.warn(s"FoundButLockedCouldNotCopy- unable to copy relationship for ITSA")
                           FoundButLockedCouldNotCopy
                       }
                       .recover {
                         case NonFatal(ex) =>
                           logger.warn(
                             s"Failed to copy CESA relationship for ${arn.value}, ${mtdItId.value} (${mtdItId.getClass.getName})",
                             ex)
                           auditService.sendCreateRelationshipAuditEvent
                           mark("Count-CopyRelationship-ITSA-FoundAndFailedToCopy")
                           FoundAndFailedToCopy
                       } else Future.successful(NotFound)
        } yield result
    }
  }

  private def findOrCreateRelationshipCopyRecordAndCopy(
    references: Set[RelationshipReference],
    maybeRelationshipCopyRecord: Option[RelationshipCopyRecord],
    arn: Arn,
    identifier: TaxIdentifier
  )(implicit ec: ExecutionContext, hc: HeaderCarrier, auditData: AuditData) =
    maybeRelationshipCopyRecord
      .map(
        relationshipCopyRecord =>
          createRelationshipsService
            .resumeRelationshipCreation(relationshipCopyRecord, arn, identifier))
      .getOrElse(
        createRelationshipsService
          .createRelationship(
            arn,
            identifier,
            references,
            failIfCreateRecordFails = true,
            failIfAllocateAgentInESFails = false))

  private def checkESForOldRelationshipAndCopyForMtdVat(arn: Arn, vrn: Vrn)(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier,
    request: Request[Any],
    auditData: AuditData): Future[CheckAndCopyResult] = {

    auditData.set("Journey", "CopyExistingESRelationship")
    auditData.set("service", "mtd-vat")
    auditData.set("vrn", vrn)

    relationshipCopyRepository.findBy(arn, vrn).flatMap {
      case Some(relationshipCopyRecord) if !relationshipCopyRecord.actionRequired =>
        //logger.warn(s"Relationship has been already been found in ES and we have already attempted to copy to MTD")
        Future successful AlreadyCopiedDidNotCheck
      case maybeRelationshipCopyRecord @ _ =>
        for {
          references <- lookupESForOldRelationship(arn, vrn)
          result <- if (references.nonEmpty)
                     findOrCreateRelationshipCopyRecordAndCopy(
                       references.map(VatRef.apply),
                       maybeRelationshipCopyRecord,
                       arn,
                       vrn)
                       .map {
                         case Some(_) =>
                           auditService.sendCreateRelationshipAuditEventForMtdVat
                           mark("Count-CopyRelationship-VAT-FoundAndCopied")
                           FoundAndCopied
                         case None =>
                           auditService.sendCreateRelationshipAuditEventForMtdVat
                           mark("Count-CopyRelationship-VAT-FoundButLockedCouldNotCopy")
                           logger.warn(s"FoundButLockedCouldNotCopy- unable to copy relationship for MTD-VAT")
                           FoundButLockedCouldNotCopy
                       }
                       .recover {
                         case NonFatal(ex) =>
                           logger.warn(
                             s"Failed to copy ES relationship for ${arn.value}, $vrn due to: ${ex.getMessage}",
                             ex)
                           auditService.sendCreateRelationshipAuditEventForMtdVat
                           mark("Count-CopyRelationship-VAT-FoundAndFailedToCopy")
                           FoundAndFailedToCopy
                       } else Future.successful(NotFound)
        } yield result
    }
  }

  def lookupCesaForOldRelationship(arn: Arn, nino: Nino)(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier,
    request: Request[Any],
    auditData: AuditData): Future[Set[SaAgentReference]] = {
    auditData.set("nino", nino)
    for {
      references <- des.getClientSaAgentSaReferences(nino)
      matching <- intersection(references) {
                   mapping.getSaAgentReferencesFor(arn)
                 }
      _ = auditData.set("saAgentRef", matching.mkString(","))
      _ = auditData.set("CESARelationship", matching.nonEmpty)
    } yield {
      auditService.sendCheckCESAAuditEvent
      matching
    }
  }

  def lookupESForOldRelationship(arn: Arn, clientVrn: Vrn)(
    implicit
    ec: ExecutionContext,
    hc: HeaderCarrier,
    request: Request[Any],
    auditData: AuditData): Future[Set[AgentCode]] = {
    auditData.set("vrn", clientVrn)

    for {
      agentGroupIds <- es.getDelegatedGroupIdsForHMCEVATDECORG(clientVrn)
      groupInfos = agentGroupIds.map(ugs.getGroupInfo)
      agentCodes <- Future
                     .sequence(groupInfos)
                     .map { setOptions: Set[Option[GroupInfo]] =>
                       setOptions
                         .flatMap { maybeGroup =>
                           maybeGroup.map(_.agentCode)
                         }
                         .collect { case Some(ac) => ac }
                     }
      matching <- intersection[AgentCode](agentCodes.toSeq) {
                   mapping.getAgentCodesFor(arn)
                 }
      _ = auditData.set("oldAgentCodes", matching.map(_.value).mkString(","))
      _ = auditData.set("ESRelationship", matching.nonEmpty)
    } yield {
      auditService.sendCheckESAuditEvent
      matching
    }
  }

  private def intersection[A](referenceIds: Seq[A])(mappingServiceCall: => Future[Seq[A]])(
    implicit ec: ExecutionContext): Future[Set[A]] = {
    val referenceIdSet = referenceIds.toSet

    if (referenceIdSet.isEmpty) {
      //logger.warn(s"The references (${referenceIdSet.getClass.getName}) in cesa/es are empty.")
      returnValue(Set.empty)
    } else
      mappingServiceCall.map { mappingServiceIds =>
        val intersected = mappingServiceIds.toSet.intersect(referenceIdSet)
        logger.info(
          s"The CESA SA references have been found, " +
            s"${if (intersected.isEmpty) "but no previous relationship exists"
            else "and will attempt to copy existing relationship"}")
        intersected
      }
  }

  def cleanCopyStatusRecord(arn: Arn, mtdItId: MtdItId)(implicit executionContext: ExecutionContext): Future[Unit] =
    relationshipCopyRepository.remove(arn, mtdItId).flatMap { n =>
      if (n == 0)
        Future.failed(RelationshipNotFound("Nothing has been removed from db."))
      else {
        logger.warn(s"Copy status record(s) has been removed for ${arn.value}, ${mtdItId.value}: $n")
        Future.successful(())
      }
    }
}
