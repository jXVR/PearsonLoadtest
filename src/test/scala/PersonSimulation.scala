import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import fabricator._
import java.time.ZonedDateTime

class PersonSimulation extends Simulation {

  val contact = fabricator.Contact()
  val alpha = fabricator.Alphanumeric()
  val pageId = Iterator.continually(List(2, 3, 4, 5)).flatten

  val PersonFeeder = Iterator.continually(Map(
    "firstName" -> Seq.fill(10)(contact.firstName),
    "age" -> Seq.fill(10)(alpha.randomInt(1, 99)),
    "timestamp" -> ZonedDateTime.now().toInstant().toEpochMilli()
  ))

  val PageFeeder = Iterator.continually(Map(
    "pageNumber" -> pageId.next()
  ))

  val httpProtocol = http
    .maxConnectionsPerHost(1)
    .disableCaching
    .baseUrl("http://144.126.245.135")
    .header("Accept", "application/json")
    .header("Content-Type", "application/json")

  object PostPersons {
    val postPersons = repeat(10) {
      feed(PersonFeeder).
      exec(
        http("Create person from JSON")
          .post("/persons")
          .body(ElFileBody("bodies/SinglePersonBody.json")).asJson
      )
        .pause(15)
    }
  }

  object PostPersonsBulk {
    val postPersonsBulk = repeat(10) {
      feed(PersonFeeder).
      exec(
        http("Create persons in bulk from JSON")
          .post("/persons/bulk")
          .body(ElFileBody("bodies/BulkPersonBody.json")).asJson
      )
        .pause(15)
    }
  }

  object GetPersonsPage {
    val getPersonsFirstPage = repeat(1) {
      exec(
        http("Get persons Page 1")
          .get("/persons/1")
          .check(jsonPath("$[*].name").findAll.saveAs("nameList")
          )
      )
        .pause(150)
    }

    val getPersonsPage = repeat(4) {
      feed(PageFeeder).
        exec(
          http("Get persons Page ${pageNumber}")
            .get("/persons/${pageNumber}")
        )
        .pause(25)
    }
  }

  object GetPersonsName {
    val getPersonsName = repeat(10) {
      exec(
        http("Get Person Name")
          .get("/persons/name/${nameList.random()}")
      )
        .pause(25)
    }
  }

  val singleUser = scenario("Single User")
    .exec(PostPersons.postPersons,
        GetPersonsPage.getPersonsFirstPage,
        GetPersonsPage.getPersonsPage,
        GetPersonsName.getPersonsName
    )

  val bulkUser = scenario("Bulk User")
    .exec(PostPersonsBulk.postPersonsBulk,
      GetPersonsPage.getPersonsFirstPage,
      GetPersonsPage.getPersonsPage,
      GetPersonsName.getPersonsName
    )

  setUp(
    singleUser.inject(
        rampConcurrentUsers(1).to(100).during(600),
        constantConcurrentUsers(100).during(3000)),
    bulkUser.inject(
        constantConcurrentUsers(1).during(3 second),
        rampConcurrentUsers(1).to(100).during(600),
        constantConcurrentUsers(100).during(3000))
  ).protocols(httpProtocol)
}
