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

import javax.inject.{Inject, Named, Singleton}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientrelationships.model.RelationshipStatus.Active
import uk.gov.hmrc.agentclientrelationships.model.{Relationship, RelationshipStatus}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration

@Singleton
class AgentFiRelationshipRepository @Inject()(
  @Named("inactive-relationships.show-last-days") showInactiveRelationshipsDuration: Duration,
  mongoComponent: ReactiveMongoComp)
    extends ReactiveRepository[Relationship, BSONObjectID](
      "fi-relationship",
      mongoComponent.agentFiRelationshipDb,
      Relationship.relationshipFormat,
      ReactiveMongoFormats.objectIdFormats)
    with StrictlyEnsureIndexes[Relationship, BSONObjectID] {

//  override def indexes: Seq[Index] =
//    Seq(
//      Index(
//        Seq(
//          "arn"                -> IndexType.Ascending,
//          "service"            -> IndexType.Ascending,
//          "clientId"           -> IndexType.Ascending,
//          "relationshipStatus" -> IndexType.Ascending),
//        Some("Arn_Service_ClientId_RelationshipStatus")
//      ),
//      Index(
//        Seq("clientId" -> IndexType.Ascending, "relationshipStatus" -> IndexType.Ascending),
//        Some("ClientId_RelationshipStatus")),
//      Index(
//        Seq("arn" -> IndexType.Ascending, "relationshipStatus" -> IndexType.Ascending),
//        Some("Arn_RelationshipStatus")),
//      Index(
//        Seq(
//          "service"            -> IndexType.Ascending,
//          "clientId"           -> IndexType.Ascending,
//          "relationshipStatus" -> IndexType.Ascending),
//        Some("Service_ClientId_RelationshipStatus")
//      ),
//      Index(
//        Seq("arn" -> IndexType.Ascending, "service" -> IndexType.Ascending, "clientId" -> IndexType.Ascending),
//        Some("Arn_Service")),
//      Index(Seq("service" -> IndexType.Ascending, "clientId" -> IndexType.Ascending), Some("Service_ClientId"))
//    )

  def findRelationships(arn: String, service: String, clientId: String, status: RelationshipStatus = Active)(
    implicit ec: ExecutionContext): Future[List[Relationship]] =
    find("arn" -> arn, "service" -> service, "clientId" -> clientId.replaceAll(" ", ""), "relationshipStatus" -> status)

  def findAnyRelationships(arn: String, service: String, clientId: String)(
    implicit ec: ExecutionContext): Future[List[Relationship]] =
    find("arn" -> arn, "service" -> service, "clientId" -> clientId.replaceAll(" ", ""))

  def createRelationship(relationship: Relationship)(implicit ec: ExecutionContext): Future[Unit] =
    insert(relationship.copy(clientId = relationship.clientId.replaceAll(" ", "")))
      .map(_ => ())
}
