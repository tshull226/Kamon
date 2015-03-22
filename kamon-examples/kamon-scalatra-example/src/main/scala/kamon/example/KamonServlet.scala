package kamon.example

import org.scalatra.kamon.KamonSupport
import org.scalatra.{AsyncResult, FutureSupport, ScalatraServlet}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class KamonServlet extends ScalatraServlet with KamonSupport with FutureSupport {


  get("/async"){
    withTrace("asyncTrace") {
      new AsyncResult {
        val is =
          Future {
            Thread.sleep(Random.nextInt(100))
          }
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
