
import chisel3._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import chiseltest._
import chiseltest.experimental.TestOptionBuilder.ChiselScalatestOptionBuilder
import chiseltest.internal.WriteVcdAnnotation
import cpu.ifu.BPU
import org.scalatest.FlatSpec

class TopDebugTest extends FlatSpec with ChiselScalatestTester {
  (new ChiselStage).execute(Array.empty, Seq(ChiselGeneratorAnnotation(() => new Top)))
  "TopDebug" should "debug" in {
    test(new TopDebug).withAnnotations(Seq(WriteVcdAnnotation)) {
      i =>
        i.clock.step(10)
        i.io.rxd.uart_data.poke("hff".U(8.W))
        i.io.rxd.uart_ready.poke(true.B)
        i.clock.step(1)
        i.io.rxd.uart_data.poke("h00".U(8.W))
        i.io.rxd.uart_ready.poke(false.B)
        i.clock.step(10)
        i.io.rxd.uart_data.poke("hff".U(8.W))
        i.io.rxd.uart_ready.poke(true.B)
        i.clock.step(1)
        i.io.rxd.uart_data.poke("h00".U(8.W))
        i.io.rxd.uart_ready.poke(false.B)
        i.clock.step(10)
        i.io.rxd.uart_data.poke("hff".U(8.W))
        i.io.rxd.uart_ready.poke(true.B)
        i.clock.step(1)
        i.io.rxd.uart_data.poke("h00".U(8.W))
        i.io.rxd.uart_ready.poke(false.B)
        i.clock.step(10)
        i.io.rxd.uart_data.poke("hff".U(8.W))
        i.io.rxd.uart_ready.poke(true.B)
        i.clock.step(1)
        i.io.rxd.uart_data.poke("h00".U(8.W))
        i.io.rxd.uart_ready.poke(false.B)
        i.clock.step(800)
    }
  }
}
