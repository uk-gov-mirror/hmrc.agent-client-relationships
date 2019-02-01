/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientrelationships.repository

import java.util.concurrent.RejectedExecutionHandler

import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime.now
import org.joda.time.DateTimeZone.UTC
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import play.api.libs.json.Json.format
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.{CursorProducer, ReadPreference}
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson.{BSONDateTime, BSONDocument, BSONInteger, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers
import uk.gov.hmrc.agentclientrelationships.model.TypeOfEnrolment
import uk.gov.hmrc.agentclientrelationships.repository.DeleteRecord.formats
import uk.gov.hmrc.agentclientrelationships.repository.SyncStatus._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.http.{HeaderCarrier, Token}
import uk.gov.hmrc.http.logging.{Authorization, RequestChain, SessionId}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{AtomicUpdate, ReactiveRepository}

import scala.concurrent.{ExecutionContext, Future}

case class DeleteRecord(
  arn: String,
  clientIdentifier: String,
  clientIdentifierType: String,
  dateTime: DateTime = now(UTC),
  syncToETMPStatus: Option[SyncStatus] = None,
  syncToESStatus: Option[SyncStatus] = None,
  lastRecoveryAttempt: Option[DateTime] = None,
  numberOfAttempts: Int = 0,
  headerCarrier: Option[HeaderCarrier] = None) {
  def actionRequired: Boolean = needToDeleteEtmpRecord || needToDeleteEsRecord

  def needToDeleteEtmpRecord = !(syncToETMPStatus.contains(Success) || syncToETMPStatus.contains(InProgress))

  def needToDeleteEsRecord = !(syncToESStatus.contains(Success) || syncToESStatus.contains(InProgress))
}

object DeleteRecord {
  implicit val hcWrites = new OWrites[HeaderCarrier] {
    override def writes(hc: HeaderCarrier): JsObject =
      JsObject(
        Seq(
          "authorization" -> hc.authorization.map(_.value),
          "sessionId"     -> hc.sessionId.map(_.value),
          "token"         -> hc.token.map(_.value),
          "gaToken"       -> hc.gaToken
        ).collect {
          case (key, Some(value)) => (key, JsString(value))
        })
  }

  import play.api.libs.functional.syntax._

  implicit val reads: Reads[HeaderCarrier] = (
    (JsPath \ "authorization").readNullable[String].map(_.map(Authorization.apply)) and
      (JsPath \ "sessionId").readNullable[String].map(_.map(SessionId.apply)) and
      (JsPath \ "token").readNullable[String].map(_.map(Token.apply)) and
      (JsPath \ "gaToken").readNullable[String]
  )((a, s, t, g) => HeaderCarrier(authorization = a, sessionId = s, token = t, gaToken = g))

  import ReactiveMongoFormats._

  implicit val formats: Format[DeleteRecord] = format[DeleteRecord]
}

trait DeleteRecordRepository {
  def create(record: DeleteRecord)(implicit ec: ExecutionContext): Future[Int]
  def findBy(arn: Arn, identifier: TaxIdentifier)(implicit ec: ExecutionContext): Future[Option[DeleteRecord]]
  def updateEtmpSyncStatus(arn: Arn, identifier: TaxIdentifier, status: SyncStatus)(
    implicit ec: ExecutionContext): Future[Unit]
  def updateEsSyncStatus(arn: Arn, identifier: TaxIdentifier, status: SyncStatus)(
    implicit ec: ExecutionContext): Future[Unit]
  def markRecoveryAttempt(arn: Arn, identifier: TaxIdentifier)(implicit ec: ExecutionContext): Future[Unit]
  def remove(arn: Arn, identifier: TaxIdentifier)(implicit ec: ExecutionContext): Future[Int]
  def selectNextToRecover(implicit executionContext: ExecutionContext): Future[Option[DeleteRecord]]
}

@Singleton
class MongoDeleteRecordRepository @Inject()(mongoComponent: ReactiveMongoComponent)
    extends ReactiveRepository[DeleteRecord, BSONObjectID](
      "delete-record",
      mongoComponent.mongoConnector.db,
      formats,
      ReactiveMongoFormats.objectIdFormats)
    with DeleteRecordRepository
    with StrictlyEnsureIndexes[DeleteRecord, BSONObjectID]
    with AtomicUpdate[DeleteRecord] {

  import ImplicitBSONHandlers._
  import play.api.libs.json.Json.JsValueWrapper

  private def clientIdentifierType(identifier: TaxIdentifier) = TypeOfEnrolment(identifier).identifierKey

  override def indexes =
    Seq(
      Index(
        Seq("arn" -> Ascending, "clientIdentifier" -> Ascending, "clientIdentifierType" -> Ascending),
        Some("arnAndAgentReference"),
        unique = true))

  def create(record: DeleteRecord)(implicit ec: ExecutionContext): Future[Int] =
    insert(record).map { result =>
      result.writeErrors.foreach(error => Logger(getClass).warn(s"Creating DeleteRecord failed: ${error.errmsg}"))
      result.n
    }

  def findBy(arn: Arn, identifier: TaxIdentifier)(implicit ec: ExecutionContext): Future[Option[DeleteRecord]] =
    find(
      "arn"                  -> arn.value,
      "clientIdentifier"     -> identifier.value,
      "clientIdentifierType" -> clientIdentifierType(identifier))
      .map(_.headOption)

  def updateEtmpSyncStatus(arn: Arn, identifier: TaxIdentifier, status: SyncStatus)(
    implicit ec: ExecutionContext): Future[Unit] =
    atomicUpdate(
      finder = BSONDocument(
        "arn"                            -> arn.value,
        "clientIdentifier"               -> identifier.value,
        "clientIdentifierType"           -> clientIdentifierType(identifier)),
      modifierBson = BSONDocument("$set" -> BSONDocument("syncToETMPStatus" -> status.toString))
    ).map(_.foreach { update =>
      update.writeResult.errmsg.foreach(error =>
        Logger(getClass).warn(s"Updating ETMP sync status ($status) failed: $error"))
    })

  def updateEsSyncStatus(arn: Arn, identifier: TaxIdentifier, status: SyncStatus)(
    implicit ec: ExecutionContext): Future[Unit] =
    atomicUpdate(
      finder = BSONDocument(
        "arn"                            -> arn.value,
        "clientIdentifier"               -> identifier.value,
        "clientIdentifierType"           -> clientIdentifierType(identifier)),
      modifierBson = BSONDocument("$set" -> BSONDocument("syncToESStatus" -> status.toString))
    ).map(_.foreach { update =>
      update.writeResult.errmsg.foreach(error =>
        Logger(getClass).warn(s"Updating ES sync status ($status) failed: $error"))
    })

  def markRecoveryAttempt(arn: Arn, identifier: TaxIdentifier)(implicit ec: ExecutionContext): Future[Unit] =
    atomicUpdate(
      finder = BSONDocument(
        "arn"                  -> arn.value,
        "clientIdentifier"     -> identifier.value,
        "clientIdentifierType" -> clientIdentifierType(identifier)),
      modifierBson = BSONDocument(
        "$set" -> BSONDocument("lastRecoveryAttempt" -> BSONDateTime(DateTime.now(DateTimeZone.UTC).getMillis)),
        "$inc" -> BSONDocument("numberOfAttempts"    -> BSONInteger(1))
      )
    ).map(_.foreach { update =>
      update.writeResult.errmsg.foreach(error => Logger(getClass).warn(s"Marking recovery attempt failed: $error"))
    })

  def remove(arn: Arn, identifier: TaxIdentifier)(implicit ec: ExecutionContext): Future[Int] =
    remove(
      "arn"                  -> arn.value,
      "clientIdentifier"     -> identifier.value,
      "clientIdentifierType" -> clientIdentifierType(identifier))
      .map(_.n)

  override def selectNextToRecover(implicit ec: ExecutionContext): Future[Option[DeleteRecord]] =
    collection
      .find(Json.obj())
      .sort(JsObject(Seq("lastRecoveryAttempt" -> JsNumber(1))))
      .cursor[DeleteRecord](ReadPreference.primaryPreferred)(
        implicitly[collection.pack.Reader[DeleteRecord]],
        implicitly[CursorProducer[DeleteRecord]])
      .headOption

  override def isInsertion(newRecordId: BSONObjectID, oldRecord: DeleteRecord): Boolean = false
}
