package cpu.ifu

import chisel3._
import chiseltest._
import chiseltest.experimental.TestOptionBuilder.ChiselScalatestOptionBuilder
import chiseltest.internal.WriteVcdAnnotation
import cpu.exu.Decode
import org.scalatest.FlatSpec

class DecodeTest extends FlatSpec with ChiselScalatestTester {
  "Decode" should "decode" in {
    test(new Decode).withAnnotations(Seq(WriteVcdAnnotation)){
      i=>
        i.io.fb_inst_bank.valid.poke(true.B)
        i.io.fb_inst_bank.bits.data(0).is_valid.poke(false.B)
        i.io.fb_inst_bank.bits.data(1).is_valid.poke(true.B)
        i.io.fb_inst_bank.bits.data(0).inst.poke("h01294820".U(32.W))
        i.io.fb_inst_bank.bits.data(1).inst.poke("h2149ff9c".U(32.W))
        i.clock.step()

    }
  }
}
