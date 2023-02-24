package io.lenses.connect.secrets.testcontainers

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network

abstract class SingleContainer[T <: GenericContainer[_]] {

  def container: T

  def start(): Unit = container.start()

  def stop(): Unit = container.stop()

  def withNetwork(network: Network): this.type = {
    container.withNetwork(network)
    this
  }

  def withExposedPorts(ports: Integer*): this.type = {
    container.withExposedPorts(ports: _*)
    this
  }
}
