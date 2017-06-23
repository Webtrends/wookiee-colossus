package com.webtrends.harness.component.colossus.command

import akka.actor.ActorSystem
import colossus.protocols.http.HttpMethod._
import colossus.protocols.http.UrlParsing._
import colossus.protocols.http.{HttpBody, HttpBodyEncoders, HttpCodes, HttpHeader, HttpHeaders, HttpMethod, HttpRequest}
import colossus.service.{Callback, CallbackExecutor}
import colossus.testkit.{CallbackAwait, CallbackMatchers, ColossusSpec, FakeIOSystem}
import com.webtrends.harness.component.colossus.http.{AllowedHeaders, CORS, Settings}
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.Future
import scala.concurrent.duration._

class CORSTest extends ColossusSpec(ActorSystem("CORSTest"))
  with BeforeAndAfterAll
  with CallbackMatchers
  with CORS
  with HttpBodyEncoders{

  override def afterAll(): Unit = {
    system.terminate()
  }

  implicit val cbe: CallbackExecutor = FakeIOSystem.fakeExecutorWorkerRef.callbackExecutor

  "cors" must {

    "handle pre-flight requests" in {

      val cb = Callback.fromFuture(cors(Settings(allowedMethods = Seq(HttpMethod.Post)), {
        case req@Post on Root / "test" => Future.successful(ColossusResponse("should not respond to pre-flight requests"))
      })(
        HttpRequest(Options, "/test", HttpBody.NoBody)
          .withHeader(HttpHeader("Origin", "www.example.com"))
          .withHeader(HttpHeader("Access-Control-Request-Method", "POST"))
      ))

      val res = CallbackAwait.result(cb, 1.second)

      res.code mustEqual HttpCodes.OK
      res.body mustEqual HttpBody.NoBody
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Origin", "www.example.com"))
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Methods", "POST"))
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Credentials", "true"))
    }

    "use option if allowedMethods empty" in {
      val cb = Callback.fromFuture(cors(Settings(), {
        case req@Post on Root / "test" => Future.successful(ColossusResponse("should not respond to pre-flight requests"))
      })(
        HttpRequest(Options, "/test", HttpBody.NoBody)
          .withHeader(HttpHeader("Origin", "www.example.com"))
          .withHeader(HttpHeader("Access-Control-Request-Method", "POST"))
      ))

      val res = CallbackAwait.result(cb, 1.second)

      res.code mustEqual HttpCodes.OK
      res.body mustEqual HttpBody.NoBody
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Origin", "www.example.com"))
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Methods", "OPTIONS"))
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Credentials", "true"))
    }

    "handle Access-Control-Request-Headers in pre-flight requests" in {

      val cb = Callback.fromFuture(cors(Settings(
        allowedMethods = Seq(HttpMethod.Post),
        accessControlAllowHeaders = AllowedHeaders.Matching(HttpHeaders.ContentType)
      ), {
        case req@Post on Root / "test" => Future.successful(ColossusResponse("should not respond to pre-flight requests"))
      })(
        HttpRequest(Options, "/test", HttpBody.NoBody)
          .withHeader(HttpHeader("Origin", "www.example.com"))
          .withHeader(HttpHeader("Access-Control-Request-Method", "POST"))
          .withHeader(HttpHeader("Access-Control-Request-Headers", Seq(HttpHeaders.ContentType, HttpHeaders.Accept).mkString(",")))
      ))

      val res = CallbackAwait.result(cb, 1.second)

      res.code mustEqual HttpCodes.OK
      res.body mustEqual HttpBody.NoBody
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Origin", "www.example.com"))
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Methods", "POST"))
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Credentials", "true"))
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Headers", "content-type"))
    }

    "handle Access-Control-Request-Headers in pre-flight requests when header list has whitespace" in {

      val requestHeaders = "content-type, accept"

      val cb = Callback.fromFuture(cors(Settings(
        allowedMethods = Seq(HttpMethod.Post),
        accessControlAllowHeaders = AllowedHeaders.Matching("Content-Type", "Accept")
      ), {
        case req@Post on Root / "test" => Future.successful(ColossusResponse("should not respond to pre-flight requests"))
      })(
        HttpRequest(Options, "/test", HttpBody.NoBody)
          .withHeader(HttpHeader("Origin", "www.example.com"))
          .withHeader(HttpHeader("Access-Control-Request-Method", "POST"))
          .withHeader(HttpHeader("Access-Control-Request-Headers", requestHeaders))
      ))

      val res = CallbackAwait.result(cb, 1.second)

      res.code mustEqual HttpCodes.OK
      res.body mustEqual HttpBody.NoBody
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Origin", "www.example.com"))
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Methods", "POST"))
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Credentials", "true"))
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Headers", requestHeaders))
    }

    "handle case sensitivity with Access-Control-Request-Headers in pre-flight requests" in {


      val cb = Callback.fromFuture(cors(Settings(
        allowedMethods = Seq(HttpMethod.Post),
        accessControlAllowHeaders = AllowedHeaders.Matching("cOnTent-TypE", "AccepT")
      ), {
        case req@Post on Root / "test" => Future.successful(ColossusResponse("should not respond to pre-flight requests"))
      })(
        HttpRequest(Options, "/test", HttpBody.NoBody)
          .withHeader(HttpHeader("Origin", "www.example.com"))
          .withHeader(HttpHeader("Access-Control-Request-Method", "POST"))
          .withHeader(HttpHeader("Access-Control-Request-Headers", "content-Type, aCCEPT"))
      ))

      val res = CallbackAwait.result(cb, 1.second)

      res.code mustEqual HttpCodes.OK
      res.body mustEqual HttpBody.NoBody
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Origin", "www.example.com"))
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Methods", "POST"))
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Credentials", "true"))
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Headers", "content-type, accept"))
    }

    "handle Access-Control-Request-Headers in pre-flight requests when all request headers are allowed" in {

      val allowHeaders = Seq(HttpHeaders.ContentType, HttpHeaders.Accept).mkString(",")

      val cb = Callback.fromFuture(cors(Settings(
        allowedMethods = Seq(HttpMethod.Post),
        accessControlAllowHeaders = AllowedHeaders.`*`), {
        case req@Post on Root / "test" => Future.successful(ColossusResponse("should not respond to pre-flight requests"))
      })(
        HttpRequest(Options, "/test", HttpBody.NoBody)
          .withHeader(HttpHeader("Origin", "www.example.com"))
          .withHeader(HttpHeader("Access-Control-Request-Method", "POST"))
          .withHeader(HttpHeader("Access-Control-Request-Headers", allowHeaders))
      ))

      val res = CallbackAwait.result(cb, 1.second)

      res.code mustEqual HttpCodes.OK
      res.body mustEqual HttpBody.NoBody
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Origin", "www.example.com"))
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Methods", "POST"))
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Credentials", "true"))
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Headers", "content-type, accept"))
    }

    "allow inner route to be executed on non-cors requests" in {
      val cb = Callback.fromFuture(cors(Settings(allowedMethods = Seq(HttpMethod.Post)), {
        case req@Put on Root / "test" => Future.successful(ColossusResponse("executed"))
      })(
        HttpRequest(Put, "/test", HttpBody.NoBody)
      ))

      val res = CallbackAwait.result(cb, 1.second)

      res.code mustEqual HttpCodes.OK
      res.body.toString mustEqual "executed"
      res.headers.toSeq must (not contain HttpHeader("Access-Control-Allow-Origin", "www.example.com"))
      res.headers.toSeq must (not contain HttpHeader("Access-Control-Allow-Credentials", "true"))
    }

    "allow inner route to be executed on cors request" in {
      val cb = Callback.fromFuture(cors(Settings(allowedMethods = Seq(HttpMethod.Put)), {
        case req@Put on Root / "test" => Future.successful(ColossusResponse("executed"))
      })(
        HttpRequest(Put, "/test", HttpBody.NoBody)
          .withHeader(HttpHeader("Origin", "www.example.com"))
      ))

      val res = CallbackAwait.result(cb, 1.second)

      res.code mustEqual HttpCodes.OK
      res.body.toString mustEqual "executed"
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Origin", "www.example.com"))
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Credentials", "true"))
    }

    "not respond with 'Access-Control-Allow-Headers' header if allowedHeaders is empty" in {
      val cb = Callback.fromFuture(cors(Settings(allowedMethods = Seq(HttpMethod.Put)), {
        case req@Put on Root / "test" => Future.successful(ColossusResponse("executed"))
      })(
        HttpRequest(Options, "/test", HttpBody.NoBody)
          .withHeader(HttpHeader("Origin", "www.example.com"))
          .withHeader(HttpHeader("Access-Control-Request-Method", Put.name))
      ))

      val res = CallbackAwait.result(cb, 1.second)

      res.code mustEqual HttpCodes.OK
      res.body mustEqual HttpBody.NoBody
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Origin", "www.example.com"))
      res.headers.toSeq must contain(HttpHeader("Access-Control-Allow-Credentials", "true"))
      res.headers.firstValue("Access-Control-Allow-Headers") must (not equal Some(""))
      res.headers.firstValue("Access-Control-Allow-Headers") mustBe None

    }
  }
}
