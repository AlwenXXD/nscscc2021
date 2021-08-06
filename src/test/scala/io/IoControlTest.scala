package io

import chisel3._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import chiseltest._
import chiseltest.experimental.TestOptionBuilder.ChiselScalatestOptionBuilder
import chiseltest.internal.WriteVcdAnnotation
import cpu.ifu.BPU
import org.scalatest.FlatSpec

class IoControlTest extends FlatSpec with ChiselScalatestTester {
  (new ChiselStage).execute(Array.empty, Seq(ChiselGeneratorAnnotation(() => new IoControl)))
  "io" should "run" in {
    test(new IoControl).withAnnotations(Seq(WriteVcdAnnotation)){
      i=>
        i.io.icache_read_resp.ready.poke(true.B)
        i.io.dcache_read_resp.ready.poke(true.B)
        i.io.base_ram_ctrl.data_in.poke("h11111111".U(32.W))

        i.io.icache_read_req.bits.addr.poke("h80000000".U(32.W))
        i.io.icache_read_req.valid.poke(true.B)
        i.io.dcache_read_req.bits.addr.poke("h80000000".U(32.W))
        i.io.dcache_read_req.valid.poke(true.B)
        i.io.dcache_write_req.bits.addr.poke("h80000000".U(32.W))
        i.io.dcache_write_req.bits.data.poke("hffffffff".U(32.W))
        i.io.dcache_write_req.bits.byte_mask.poke("b1111".U(4.W))
        i.io.dcache_write_req.valid.poke(false.B)
        i.clock.step(5)
        i.io.icache_read_req.bits.addr.poke("h80000000".U(32.W))
        i.io.icache_read_req.valid.poke(false.B)
        i.io.dcache_read_req.bits.addr.poke("h80000000".U(32.W))
        i.io.dcache_read_req.valid.poke(true.B)
        i.io.dcache_write_req.bits.addr.poke("h80000000".U(32.W))
        i.io.dcache_write_req.bits.data.poke("hffffffff".U(32.W))
        i.io.dcache_write_req.bits.byte_mask.poke("b1111".U(4.W))
        i.io.dcache_write_req.valid.poke(true.B)
        i.clock.step(2)
        i.io.icache_read_req.bits.addr.poke("h80000000".U(32.W))
        i.io.icache_read_req.valid.poke(false.B)
        i.io.dcache_read_req.bits.addr.poke("h80000000".U(32.W))
        i.io.dcache_read_req.valid.poke(false.B)
        i.io.dcache_write_req.bits.addr.poke("h80000000".U(32.W))
        i.io.dcache_write_req.bits.data.poke("hffffffff".U(32.W))
        i.io.dcache_write_req.bits.byte_mask.poke("b1111".U(4.W))
        i.io.dcache_write_req.valid.poke(true.B)
        i.clock.step(3)
    }
  }
}

