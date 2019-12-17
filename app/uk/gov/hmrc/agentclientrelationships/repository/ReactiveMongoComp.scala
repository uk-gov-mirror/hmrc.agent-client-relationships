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

import com.google.inject.{Inject, Singleton}
import play.modules.reactivemongo.{NamedDatabase, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.DefaultDB

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.language.postfixOps

@Singleton
class ReactiveMongoComp @Inject()(@NamedDatabase("agent-fi-relationship") val reactiveMongoApi: ReactiveMongoApi)(
  implicit ec: ExecutionContext)
    extends ReactiveMongoComponents {

  lazy val agentFiRelationshipDb: () => DefaultDB = () => Await.result(reactiveMongoApi.database, 3 seconds)

}
