import chisel3._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import chisel3.util.PriorityEncoder
import chiseltest._
import chiseltest.experimental.TestOptionBuilder.ChiselScalatestOptionBuilder
import chiseltest.internal.WriteVcdAnnotation
import cpu.ifu.BPU
import org.scalatest.FlatSpec

class Tester extends Module {
  val io = IO(new Bundle() {
    val input  = Input(UInt(32.W))
    val output = Output(UInt(32.W))
  })
  def EndianConvert(data:UInt) ={
    val w = data.getWidth
    VecInit((0 until w/8).map(i=>data(i*8+7,i*8)).reverse).asUInt()
  }
  io.output := EndianConvert(io.input)
}

class ChiselTest extends FlatSpec with ChiselScalatestTester {
  //(new ChiselStage).execute(Array.empty, Seq(ChiselGeneratorAnnotation(() => new Tester)))
  "ChiselTest" should "test" in {
    test(new Tester).withAnnotations(Seq(WriteVcdAnnotation)) {
      i =>
        i.io.input.poke("h76543210".U(32.W))
        i.clock.step()
        i.io.input.poke("h00112233".U(32.W))
        i.clock.step()

    }
  }
}
