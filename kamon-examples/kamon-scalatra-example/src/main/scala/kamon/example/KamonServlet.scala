package kamon.example

import dispatch._
import org.scalatra.kamon.KamonSupport
import org.scalatra.{FutureSupport, ScalatraServlet}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Random, Success, Try}

class KamonServlet extends ScalatraServlet with KamonSupport with FutureSupport {


  get("/async") {
    traceFuture("retrievePage") {
      Future {
        HttpClient.retrievePage()
      }
    }
  }

  get("/time") {
    time("time") {
      Thread.sleep(Random.nextInt(100))
    }
  }

  get("/minMaxCounter") {
    minMaxCounter("minMaxCounter").increment()
  }

  get("/counter") {
    counter("counter").increment()
  }

  get("/histogram") {
    histogram("histogram").record(Random.nextInt(10))
  }

  protected implicit def executor: ExecutionContext = ExecutionContext.Implicits.global
}

object HttpClient {
  def retrievePage()(implicit ctx: ExecutionContext): Future[String] = {
    val prom = Promise[String]()
    dispatch.Http(url("http://slashdot.org/") OK as.String) onComplete {
      case Success(content) => prom.complete(Try(content))
      case Failure(exception) => println(exception)
    }
    prom.future
  }
}