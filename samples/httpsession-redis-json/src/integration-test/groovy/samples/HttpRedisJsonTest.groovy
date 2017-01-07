package samples

import geb.spock.*
import sample.Application
import samples.pages.*
import spock.lang.Stepwise

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.web.WebAppConfiguration

/**
 * @author jitendra on 15/3/16.
 */
@Stepwise
@SpringBootTest(classes = Application)
@WebAppConfiguration
class HttpRedisJsonTest extends GebSpec {

	def'login page test'() {
		when:
		to LoginPage
		then:
		at LoginPage
	}

	def"Unauthenticated user sent to login page"() {
		when:
		via HomePage
		then:
		at LoginPage
	}

	def"Successful Login test"() {
		when:
		login()
		then:
		at HomePage
		driver.manage().cookies.find {it.name == "SESSION"}
		!driver.manage().cookies.find {it.name == "JSESSIONID"}
	}

	def"Set and get attributes in session"() {
		when:
		setAttribute("Demo Key", "Demo Value")

		then:
		at SetAttributePage
		tdKey()*.text().contains("Demo Key")
		tdKey()*.text().contains("Demo Value")
	}
}
