package uk.gov.hmrc.agentrelationships.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.joda.time.LocalDate
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentrelationships.support.WireMockSupport
import uk.gov.hmrc.domain.TaxIdentifier

trait DesStubsGet {

  me: WireMockSupport =>

  // Via Client

  val url: TaxIdentifier => String = {
    case MtdItId(mtdItId) =>
      s"/registration/relationship?ref-no=$mtdItId&agent=false&active-only=true&regime=ITSA"
    case Vrn(vrn) =>
      s"/registration/relationship?idtype=VRN&ref-no=$vrn&agent=false&active-only=true&regime=VATC&relationship=ZA01&auth-profile=ALL00001"
    case Utr(utr) =>
      s"/registration/relationship?idtype=UTR&ref-no=$utr&agent=false&active-only=true&regime=TRS"
    case Urn(urn) =>
      s"/registration/relationship?idtype=URN&referenceNumber=$urn&agent=false&active-only=true&regime=TRS"
    case CgtRef(ref) =>
      s"/registration/relationship?idtype=ZCGT&ref-no=$ref&agent=false&active-only=true&regime=CGT&relationship=ZA01&auth-profile=ALL00001"
  }

  def getActiveRelationshipsViaClient(taxIdentifier: TaxIdentifier, arn: Arn) = {
    stubFor(get(urlEqualTo(url(taxIdentifier)))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             |{
             |"relationship" :[
             |{
             |  "referenceNumber" : "${taxIdentifier.value}",
             |  "agentReferenceNumber" : "${arn.value}",
             |  "organisation" : {
             |    "organisationName": "someOrganisationName"
             |  },
             |  "dateFrom" : "2015-09-10",
             |  "dateTo" : "9999-12-31",
             |  "contractAccountCategory" : "01",
             |  "activity" : "09"
             |}
             |]
             |}""".stripMargin)))

  }

  def getInactiveRelationshipViaClient(taxIdentifier: TaxIdentifier,
                                       agentArn: String): StubMapping =
    stubFor(
      get(
        urlEqualTo(url(taxIdentifier)))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""
                         |{
                         |"relationship" :[
                         |{
                         |  "referenceNumber" : "${taxIdentifier.value}",
                         |  "agentReferenceNumber" : "$agentArn",
                         |  "organisation" : {
                         |    "organisationName": "someOrganisationName"
                         |  },
                         |  "dateFrom" : "2015-09-10",
                         |  "dateTo" : "2016-12-31",
                         |  "contractAccountCategory" : "01",
                         |  "activity" : "09"
                         |}
                         |]
                         |}""".stripMargin)))

  def getSomeActiveRelationshipsViaClient(taxIdentifier: TaxIdentifier,
                                          agentArn1: String,
                                          agentArn2: String,
                                          agentArn3: String): StubMapping =
    stubFor(
      get(
        urlEqualTo(url(taxIdentifier)))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""
                         |{
                         |"relationship" :[
                         |{
                         |  "referenceNumber" : "$taxIdentifier",
                         |  "agentReferenceNumber" : "$agentArn1",
                         |  "organisation" : {
                         |    "organisationName": "someOrganisationName"
                         |  },
                         |  "dateFrom" : "2015-09-10",
                         |  "dateTo" : "2015-12-31",
                         |  "contractAccountCategory" : "01",
                         |  "activity" : "10"
                         |},
                         |{
                         |  "referenceNumber" : "$taxIdentifier",
                         |  "agentReferenceNumber" : "$agentArn2",
                         |  "organisation" : {
                         |    "organisationName": "sayOrganisationName"
                         |  },
                         |  "dateFrom" : "2015-09-10",
                         |  "dateTo" : "2016-12-31",
                         |  "contractAccountCategory" : "02",
                         |  "activity" : "09"
                         |},
                         |{
                         |  "referenceNumber" : "$taxIdentifier",
                         |  "agentReferenceNumber" : "$agentArn3",
                         |  "organisation" : {
                         |    "organisationName": "noneOrganisationName"
                         |  },
                         |  "dateFrom" : "2014-09-10",
                         |  "dateTo" : "9999-12-31",
                         |  "contractAccountCategory" : "03",
                         |  "activity" : "11"
                         |}
                         |]
                         |}""".stripMargin)))

  def getActiveRelationshipFailsWith(taxIdentifier: TaxIdentifier,
                                     status: Int): StubMapping =
    stubFor(
      get(
        urlEqualTo(url(taxIdentifier)))
        .willReturn(aResponse()
          .withStatus(status)))

  def getActiveRelationshipFailsWithSuspended(taxIdentifier: TaxIdentifier): StubMapping =
    stubFor(
      get(
        urlEqualTo(url(taxIdentifier)))
        .willReturn(aResponse()
          .withStatus(403).withBody("AGENT_SUSPENDED")))


  //VIA Agent

  val otherInactiveRelationship: TaxIdentifier => Arn => String = {
    case MtdItId(clientId) => arn =>
      s"""
         |{
         |  "referenceNumber" : "$clientId",
         |  "agentReferenceNumber" : "${arn.value}",
         |  "individual" : {
         |    "firstName": "someName"
         |  },
         |  "dateFrom" : "2015-09-10",
         |  "dateTo" : "2015-09-21",
         |  "contractAccountCategory" : "01",
         |  "activity" : "09"
         |}
       """.stripMargin
    case Vrn(clientId) => arn =>
      s"""
         |{
         |  "referenceNumber" : "$clientId",
         |  "agentReferenceNumber" : "${arn.value}",
         |  "organisation" : {
         |    "organisationName": "someOrganisationName"
         |  },
         |  "dateFrom" : "2015-09-10",
         |  "dateTo" : "2015-09-21",
         |  "contractAccountCategory" : "01",
         |  "activity" : "09"
         |}
       """.stripMargin
    case Utr(clientId) => arn =>
      s"""
         |{
         |  "referenceNumber" : "$clientId",
         |  "agentReferenceNumber" : "${arn.value}",
         |  "organisation" : {
         |    "organisationName": "someOrganisationName"
         |  },
         |  "dateFrom" : "2015-09-10",
         |  "dateTo" : "2015-09-21",
         |  "contractAccountCategory" : "01",
         |  "activity" : "09"
         |}
       """.stripMargin
    case Urn(clientId) => arn =>
      s"""
         |{
         |  "referenceNumber" : "$clientId",
         |  "agentReferenceNumber" : "${arn.value}",
         |  "organisation" : {
         |    "organisationName": "someOrganisationName"
         |  },
         |  "dateFrom" : "2015-09-10",
         |  "dateTo" : "2015-09-21",
         |  "contractAccountCategory" : "01",
         |  "activity" : "09"
         |}
       """.stripMargin
    case CgtRef(clientId) => arn =>
      s"""
         |{
         |  "referenceNumber" : "$clientId",
         |  "agentReferenceNumber" : "${arn.value}",
         |  "organisation" : {
         |    "organisationName": "someOrganisationName"
         |  },
         |  "dateFrom" : "2015-09-10",
         |  "dateTo" : "2015-09-21",
         |  "contractAccountCategory" : "01",
         |  "activity" : "09"
         |}
       """.stripMargin
  }

  private def inactiveUrl(arn: Arn) = s"/registration/relationship?arn=${arn.value}" +
      s"&agent=true&active-only=false&regime=AGSV&from=${LocalDate.now().minusDays(30).toString}&to=${LocalDate.now().toString}"

  private val inactiveUrlClient: TaxIdentifier => String = {
    case MtdItId(mtdItId) =>
      s"/registration/relationship?ref-no=$mtdItId&agent=false&active-only=false&regime=ITSA&from=2015-01-01&to=${LocalDate.now().toString}"
    case Vrn(vrn) =>
      s"/registration/relationship?idtype=VRN&ref-no=$vrn&agent=false&active-only=false&regime=VATC&from=2015-01-01&to=${LocalDate.now().toString}&relationship=ZA01&auth-profile=ALL00001"
    case Utr(utr) =>
      s"/registration/relationship?idtype=UTR&ref-no=$utr&agent=false&active-only=false&regime=TRS&from=2015-01-01&to=${LocalDate.now().toString}"
    case Urn(urn) =>
      s"/registration/relationship?idtype=URN&referenceNumber=$urn&agent=false&active-only=false&regime=TRS&from=2015-01-01&to=${LocalDate.now().toString}"
    case CgtRef(ref) =>
      s"/registration/relationship?idtype=ZCGT&ref-no=$ref&agent=false&active-only=false&regime=CGT&from=2015-01-01&to=${LocalDate.now().toString}&relationship=ZA01&auth-profile=ALL00001"
  }

  private val agentRecordUrl: TaxIdentifier => String = {
    case Arn(arn) => s"/registration/personal-details/arn/$arn"
    case Utr(utr) => s"/registration/personal-details/utr/$utr"
  }

  def getAgentRecordForClient(taxIdentifier: TaxIdentifier): StubMapping = {
    stubFor(get(urlEqualTo(agentRecordUrl(taxIdentifier)))
      .willReturn(aResponse()
      .withStatus(200)
        .withBody(
          s"""
             | {
             |  "suspensionDetails" : {
             |    "suspensionStatus": false,
             |    "regimes": []
             |  }
             | }
             |""".stripMargin)
      )
    )
  }

  def getSuspendedAgentRecordForClient(taxIdentifier: TaxIdentifier): StubMapping = {
    stubFor(get(urlEqualTo(agentRecordUrl(taxIdentifier)))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""
             | {
             |  "suspensionDetails" : {
             |    "suspensionStatus": true,
             |    "regimes": ["ITSA"]
             |  }
             | }
             |""".stripMargin)
      )
    )
  }

  def getInactiveRelationshipsForClient(taxIdentifier: TaxIdentifier): StubMapping = {

    val individualOrOrganisationJson = if(taxIdentifier.isInstanceOf[MtdItId])
      s"""
         | "individual" : {
         |  "firstName" : "someName"
         |  },
         |""".stripMargin
    else
      s"""
         | "organisation" : {
         | "organisationName" : "someOrgName"
         | },
         |""".stripMargin

    stubFor(get(urlEqualTo(inactiveUrlClient(taxIdentifier)))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""
                     |{
                     |"relationship" :[
                     |{
                     |  "referenceNumber" : "${taxIdentifier.value}",
                     |  "agentReferenceNumber" : "ABCDE123456", """.stripMargin + individualOrOrganisationJson + s"""
                     |  "dateFrom" : "2015-09-10",
                     |  "dateTo" : "2018-09-09",
                     |  "contractAccountCategory" : "01",
                     |  "activity" : "09"
                     |},
                     |{
                     | "referenceNumber" : "${taxIdentifier.value}",
                     |  "agentReferenceNumber" : "ABCDE777777", """.stripMargin + individualOrOrganisationJson + s"""
                     |  "dateFrom" : "2019-09-09",
                     |  "dateTo" : "2050-09-09",
                     |  "contractAccountCategory" : "01",
                     |  "activity" : "09"
                     |  }
                     |]
                     |}""".stripMargin)))
  }

  def getNoInactiveRelationshipsForClient(taxIdentifier: TaxIdentifier): StubMapping = {

    stubFor(get(urlEqualTo(inactiveUrlClient(taxIdentifier)))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""
                     |{
                     |"relationship" :[
                     |{
                     |  "referenceNumber" : "${taxIdentifier.value}",
                     |  "agentReferenceNumber" : "ABCDE123456",
                     |  "individual" : {
                     |    "firstName": "someName"
                     |  },
                     |  "dateFrom" : "2015-09-10",
                     |  "dateTo" : "2050-09-09",
                     |  "contractAccountCategory" : "01",
                     |  "activity" : "09"
                     |}
                     |]
                     |}""".stripMargin)))
  }

  def getFailInactiveRelationshipsForClient(taxIdentifier: TaxIdentifier, status: Int) =
    stubFor(get(urlEqualTo(inactiveUrlClient(taxIdentifier)))
      .willReturn(aResponse().withStatus(status))
    )

  def getInactiveRelationshipsViaAgent(arn: Arn, otherTaxIdentifier: TaxIdentifier, taxIdentifier: TaxIdentifier): StubMapping = {

    val individualOrOrganisation: TaxIdentifier => String = {
    case MtdItId(_) =>
      s""" "individual" : {
         | "firstName" : "someName"
         |  },""".stripMargin
    case _ =>
      s""" "organisation" : {
         |  "organisationName" : "someOrganisationName"
         |    },""".stripMargin
    }

    stubFor(get(urlEqualTo(inactiveUrl(arn)))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""
                     |{
                     |"relationship" :[
                     |${otherInactiveRelationship(otherTaxIdentifier)(arn)},
                     |{
                     |  "referenceNumber" : "${taxIdentifier.value}",
                     |  "agentReferenceNumber" : "${arn.value}",""".stripMargin +
          individualOrOrganisation(taxIdentifier) +
                     s"""  "dateFrom" : "2015-09-10",
                     |  "dateTo" : "${LocalDate.now().toString}",
                     |  "contractAccountCategory" : "01",
                     |  "activity" : "09"
                     |}
                     |]
                     |}""".stripMargin)))
  }

  def getAgentInactiveRelationshipsButActive(encodedArn: String, agentArn: String, clientId: String): StubMapping =
    stubFor(get(urlEqualTo(inactiveUrl(Arn(agentArn))))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""
                     |{
                     |"relationship" :[
                     |{
                     |  "referenceNumber" : "$clientId",
                     |  "agentReferenceNumber" : "$agentArn",
                     |  "organisation" : {
                     |    "organisationName": "someOrganisationName"
                     |  },
                     |  "dateFrom" : "2015-09-10",
                     |  "dateTo" : "9999-09-21",
                     |  "contractAccountCategory" : "01",
                     |  "activity" : "09"
                     |}
                     |]
                     |}""".stripMargin)))

  def getFailAgentInactiveRelationships(encodedArn: String, status: Int) =
    stubFor(get(urlEqualTo(inactiveUrl(Arn(encodedArn))))
      .willReturn(aResponse()
        .withStatus(status)))

  def getFailWithSuspendedAgentInactiveRelationships(encodedArn: String) =
    stubFor(get(urlEqualTo(inactiveUrl(Arn(encodedArn))))
      .willReturn(aResponse()
        .withStatus(403).withBody("AGENT_SUSPENDED")))

  def getFailWithInvalidAgentInactiveRelationships(encodedArn: String) =
    stubFor(get(urlEqualTo(inactiveUrl(Arn(encodedArn))))
      .willReturn(aResponse()
        .withStatus(503)))

  def getAgentInactiveRelationshipsNoDateTo(arn: Arn, clientId: String): StubMapping =
    stubFor(get(urlEqualTo(inactiveUrl(arn)))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""
                     |{
                     |"relationship" :[
                     |{
                     |  "referenceNumber" : "${clientId}",
                     |  "agentReferenceNumber" : "${arn.value}",
                     |  "organisation" : {
                     |    "organisationName": "someOrganisationName"
                     |  },
                     |  "dateFrom" : "2015-09-10",
                     |  "contractAccountCategory" : "01",
                     |  "activity" : "09"
                     |}
                     |]
                     |}""".stripMargin)))

}
