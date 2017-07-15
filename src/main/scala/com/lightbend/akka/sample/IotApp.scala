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
    val deviceActor1 = probe.lastSender

    groupActor.tell(DeviceManager.RequestTrackDevice("group", "device2"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    val deviceActor2 = probe.lastSender

    groupActor.tell(DeviceManager.RequestTrackDevice("group", "device3"), probe.ref)
    probe.expectMsg(DeviceManager.DeviceRegistered)
    val deviceActor3 = probe.lastSender

    deviceActor1.tell(Device.RecordTemperature(0, 1.0), probe.ref)
    probe.expectMsg(Device.TemperatureRecorded(0))
    deviceActor2.tell(Device.RecordTemperature(1, 2.0), probe.ref)
    probe.expectMsg(Device.TemperatureRecorded(1))

    groupActor.tell(DeviceGroup.RequestAllTemperatures(0), probe.ref)

    probe.expectMsg(DeviceGroup.RespondAllTemperatures(
      requestId = 0,
      temperatures = Map(
        "device1" -> DeviceGroup.Temperature(1.0),
        "device2" -> DeviceGroup.Temperature(2.0),
        "device3" -> DeviceGroup.TemperatureNotAvailable
      )
    ))
  } finally {
    system.terminate()
  }

}
