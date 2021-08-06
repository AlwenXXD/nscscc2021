package cpu.exu.unit

import chisel3._
import chiseltest._
import chiseltest.experimental.TestOptionBuilder.ChiselScalatestOptionBuilder
import chiseltest.internal.WriteVcdAnnotation
import cpu.ifu.BPU
import org.scalatest.FlatSpec
import signal.uOP

class LsuTest extends FlatSpec with ChiselScalatestTester {
  "Lsu" should "run" in {
    test(new Lsu).withAnnotations(Seq(WriteVcdAnnotation)){
      i=>

        i.io.cache_read.ready.poke(false.B)
        i.io.cache_write.ready.poke(false.B)
        i.io.dispatch_info.valid.poke(true.B)
        i.io.dispatch_info.bits.rob_idx.poke(0.U)
        i.io.dispatch_info.bits.op1_data.poke(0.U)
        i.io.dispatch_info.bits.op2_data.poke(0.U)
        i.io.dispatch_info.bits.imm_data.poke(0.U)
        i.io.dispatch_info.bits.uop.poke(uOP.Mm_SW)
        i.clock.step()

        i.io.cache_read.ready.poke(false.B)
        i.io.cache_write.ready.poke(false.B)
        i.io.dispatch_info.valid.poke(true.B)
        i.io.dispatch_info.bits.rob_idx.poke(1.U)
        i.io.dispatch_info.bits.op1_data.poke(0.U)
        i.io.dispatch_info.bits.op2_data.poke(1.U)
        i.io.dispatch_info.bits.imm_data.poke(1.U)
        i.io.dispatch_info.bits.uop.poke(uOP.Mm_SW)
        i.clock.step()

        i.io.cache_read.ready.poke(false.B)
        i.io.cache_write.ready.poke(false.B)
        i.io.dispatch_info.valid.poke(true.B)
        i.io.dispatch_info.bits.rob_idx.poke(2.U)
        i.io.dispatch_info.bits.op1_data.poke(0.U)
        i.io.dispatch_info.bits.op2_data.poke(0.U)
        i.io.dispatch_info.bits.imm_data.poke(0.U)
        i.io.dispatch_info.bits.uop.poke(uOP.Mm_LW)
        i.clock.step()

        i.io.cache_read.ready.poke(false.B)
        i.io.cache_write.ready.poke(false.B)
        i.io.dispatch_info.valid.poke(true.B)
        i.io.dispatch_info.bits.rob_idx.poke(3.U)
        i.io.dispatch_info.bits.op1_data.poke(0.U)
        i.io.dispatch_info.bits.op2_data.poke(0.U)
        i.io.dispatch_info.bits.imm_data.poke(1.U)
        i.io.dispatch_info.bits.uop.poke(uOP.Mm_LW)
        i.clock.step()

    }
  }
}

