/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.agentrelationships.support

import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.ws.{EmptyBody, WSClient, WSRequest, WSResponse}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.http.ws.WSHttpResponse

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
object Http {

  def get(url: String)(implicit hc: HeaderCarrier, ws: WSClient): HttpResponse = perform(url) { request =>
    request.get()
  }

  def post(url: String, body: String, headers: Seq[(String, String)] = Seq.empty)(
    implicit hc: HeaderCarrier,
    ws: WSClient): HttpResponse =
    perform(url) { request =>
      request.addHttpHeaders(headers: _*).post(body)
    }

  def postEmpty(url: String)(implicit hc: HeaderCarrier, ws: WSClient): HttpResponse = perform(url) { request =>
    request.post(EmptyBody)
  }

  def putEmpty(url: String)(implicit hc: HeaderCarrier, ws: WSClient): HttpResponse = perform(url) { request =>
    request.put(EmptyBody)
  }

  def delete(url: String)(implicit hc: HeaderCarrier, ws: WSClient): HttpResponse = perform(url) { request =>
    request.delete()
  }

  private def perform(url: String)(
    fun: WSRequest => Future[WSResponse])(implicit hc: HeaderCarrier, ws: WSClient): HttpResponse =
    await(
      fun(ws.url(url).addHttpHeaders(hc.headers: _*).withRequestTimeout(20000 milliseconds)).map(WSHttpResponse.apply))

  private def await[A](future: Future[A]) = Await.result(future, Duration(10, SECONDS))

}

class Resource(path: String, port: Int) {

  private def url() = s"http://localhost:$port$path"

  def get()(implicit hc: HeaderCarrier = HeaderCarrier(), ws: WSClient): HttpResponse = Http.get(url())

  def postAsJson(body: String)(implicit hc: HeaderCarrier = HeaderCarrier(), ws: WSClient): HttpResponse =
    Http.post(url(), body, Seq(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON))

  def postEmpty()(implicit hc: HeaderCarrier = HeaderCarrier(), ws: WSClient): HttpResponse =
    Http.postEmpty(url())

  def putEmpty()(implicit hc: HeaderCarrier = HeaderCarrier(), ws: WSClient): HttpResponse =
    Http.putEmpty(url())
}
