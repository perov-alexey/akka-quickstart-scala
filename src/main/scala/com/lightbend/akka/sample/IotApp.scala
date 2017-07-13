package com.lightbend.akka.sample

import akka.actor.{ActorSystem, PoisonPill}
import akka.testkit.TestProbe

import scala.concurrent.duration._
import scala.io.StdIn

object IotApp extends App {

  implicit val system: ActorSystem = ActorSystem("iot-system")

  try {
    val supervisor = system.actorOf(IotSupervisor.props(), "iot-superisor")

    val probe = TestProbe()
    val groupActor = system.actorOf(DeviceGroup.props("group"))

    groupActor.tell(DeviceManager.RequestTrackDevice("group", "device1"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    val toShutDown = probe.lastSender

    groupActor.tell(DeviceManager.RequestTrackDevice("group", "device2"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)

    groupActor.tell(DeviceGroup.RequestDeviceList(0), probe.ref)
    probe.expectMsg(DeviceGroup.ReplyDeviceList(0, Set("device1", "device2")))

    probe.watch(toShutDown)
    toShutDown ! PoisonPill
    probe.expectTerminated(toShutDown)

    probe.awaitAssert({
      groupActor.tell(DeviceGroup.RequestDeviceList(1), probe.ref)
      probe.expectMsg(DeviceGroup.ReplyDeviceList(1, Set("device2")))
    })

    StdIn.readLine()
  } finally {
    system.terminate()
  }

}
