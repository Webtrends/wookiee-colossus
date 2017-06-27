package com.webtrends.harness.component.colossus

import com.webtrends.harness.component.StopComponent
import com.webtrends.harness.component.colossus.command.CommandRoutingTest
import com.webtrends.harness.service.messages.CheckHealth
import com.webtrends.harness.service.test.TestHarness
import org.scalatest._

// ADD NEW TEST CLASSES TO HERE
class CommandSuite extends Suites(new ColossusManagerTest, new CommandRoutingTest) with BeforeAndAfterAll {
  override def afterAll() = {
    TestHarness.harness.foreach { th =>
      val colManager = th.getComponent("wookiee-colossus").get
      colManager ! StopComponent
      colManager ! CheckHealth
    }
    super.afterAll()
  }
}
