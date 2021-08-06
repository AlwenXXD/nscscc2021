package cpu.ifu

import chisel3._
import chisel3.util._
import signal.Const._

import scala.collection.immutable.Nil

class InstPacket extends Bundle{
  val data = Vec(FETCH_WIDTH,UInt(32.W))
  val addr = UInt(32.W)
}

class ICacheReq extends Bundle{
  val addr = UInt(32.W)
}
class ICacheResp extends Bundle{
  val data = UInt((FETCH_WIDTH*32).W)
}

class ICacheDebugIO extends Bundle{
  val state = Output(Bool())
  val hit_cache =Output(Bool())
  val cache_we = Output(Bool())
  val cache_read_tag = Output(UInt(ICACHE_TAG_WIDTH.W))
  val icache_req   = Valid(new ICacheReq)
}


class ICacheIO extends Bundle {
  val icache_req   = Flipped(Decoupled(new ICacheReq))
  val icache_resp = Valid(new InstPacket)
  val io_read_req = Decoupled(new ICacheReq)
  val io_read_resp = Flipped(Decoupled(new ICacheResp))
  val icache_debug = new ICacheDebugIO
}

class ICache extends Module {
  val io  = IO(new ICacheIO)
//  val mem = Mem(128, UInt(128.W))
//  loadMemoryFromFile(mem, "C:\\Users\\Alwen\\Desktop\\mars\\test.txt")
//
//  io.icache_resp.bits.data := mem.read(io.icache_req.bits.addr(31,4)).asTypeOf(Vec(4,UInt(32.W))).reverse
//  io.icache_resp.bits.addr := io.icache_req.bits.addr
//  io.icache_resp.valid := io.icache_req.valid
//  io.icache_req.ready:=true.B
//
//  io.io_read_req.bits.addr:=0.U
//  io.io_read_req.valid:=false.B
//  io.io_read_resp.ready:=true.B
  val sIDLE::sBUSY::Nil = Enum(2)
  val state = RegInit(sIDLE)
  val cache_valid = RegInit(VecInit(Seq.fill(ICACHE_DEPTH)(false.B)))
  val cache_tag = SyncReadMem(ICACHE_DEPTH,UInt(ICACHE_TAG_WIDTH.W))
  val cache_data = Seq.fill(FETCH_WIDTH)(SyncReadMem(ICACHE_DEPTH,UInt(32.W)))
  val io_read_data = io.io_read_resp.bits.data.asTypeOf(Vec(FETCH_WIDTH,UInt(32.W)))

  val cache_en = Wire(Bool())
  val cache_we = Wire(Bool())
  val cache_read_tag = Wire(UInt(ICACHE_TAG_WIDTH.W))
  val cache_read_data = Wire(Vec(FETCH_WIDTH,UInt(32.W)))
  val cache_write_tag = Wire(UInt(ICACHE_TAG_WIDTH.W))
  val cache_write_data = Wire(Vec(FETCH_WIDTH,UInt(32.W)))
  val hit_cache = Wire(Bool())
  val cache_valid_we= WireInit(false.B)

  io.io_read_req.valid:=false.B
  io.icache_req.ready:=false.B
  io.icache_resp.bits.data:=DontCare
  io.icache_resp.valid:=false.B
  cache_en:=true.B
  cache_we := false.B
  cache_write_tag:=DontCare
  cache_write_data:=DontCare
  cache_read_tag := 0.U(ICACHE_TAG_WIDTH.W)
  cache_read_data := VecInit(Seq.fill(FETCH_WIDTH)(0.U(32.W)))
  cache_write_tag := 0.U(ICACHE_TAG_WIDTH.W)
  cache_write_data := VecInit(Seq.fill(FETCH_WIDTH)(0.U(32.W)))
  hit_cache:=cache_read_tag===io.icache_req.bits.addr(31,32-ICACHE_TAG_WIDTH)&&cache_valid(io.icache_req.bits.addr(ICACHE_OFFSET_WIDTH+ICACHE_INDEX_WIDTH-1,ICACHE_OFFSET_WIDTH))
  switch(state){
    is(sIDLE){
      when(io.icache_req.valid){
        io.io_read_req.valid:=true.B
        state:=sBUSY
      }.otherwise{
        io.io_read_req.valid:=false.B
      }
    }
    is(sBUSY){
      when(io.io_read_req.ready){
        io.io_read_req.valid:=false.B
        io.icache_resp.valid:=io.icache_req.valid
        io.icache_resp.bits.data:=io_read_data
        io.icache_req.ready:=true.B

        cache_we:=true.B
        cache_write_tag:=io.icache_req.bits.addr(31,32-ICACHE_TAG_WIDTH)
        cache_write_data:=io_read_data
        cache_valid_we := true.B
        state:=sIDLE
      }.elsewhen(!io.icache_req.valid){
        io.io_read_req.valid:=false.B
        io.icache_resp.bits.data:=cache_read_data
        io.icache_resp.valid:=false.B
        io.icache_req.ready:=false.B
        state:=sIDLE
      }.elsewhen(hit_cache){
        io.io_read_req.valid:=false.B
        io.icache_resp.bits.data:=cache_read_data
        io.icache_resp.valid:=true.B
        io.icache_req.ready:=true.B

        state:=sIDLE
      }.otherwise{
        io.io_read_req.valid:=true.B
        io.icache_resp.valid:=false.B
        io.icache_req.ready:=false.B
      }
    }
  }
  when(cache_en){
    when(cache_we){
      cache_tag.write(io.icache_req.bits.addr(ICACHE_OFFSET_WIDTH+ICACHE_INDEX_WIDTH-1,ICACHE_OFFSET_WIDTH),cache_write_tag)
      cache_data.zip(cache_write_data).foreach{case(cache_word,write_word)=>cache_word.write(io.icache_req.bits.addr(ICACHE_OFFSET_WIDTH+ICACHE_INDEX_WIDTH-1,ICACHE_OFFSET_WIDTH),write_word)}
      cache_read_tag := DontCare
      cache_read_data := DontCare
    }.otherwise{
      cache_read_tag := cache_tag.read(io.icache_req.bits.addr(ICACHE_OFFSET_WIDTH+ICACHE_INDEX_WIDTH-1,ICACHE_OFFSET_WIDTH))
      cache_read_data := VecInit(cache_data.map(i=>i.read(io.icache_req.bits.addr(ICACHE_OFFSET_WIDTH+ICACHE_INDEX_WIDTH-1,ICACHE_OFFSET_WIDTH))))
    }
  }.otherwise{
    cache_read_tag := DontCare
    cache_read_data := DontCare
  }
  when(cache_valid_we){
    cache_valid(io.icache_req.bits.addr(ICACHE_OFFSET_WIDTH+ICACHE_INDEX_WIDTH-1,ICACHE_OFFSET_WIDTH)):=true.B
  }

  io.io_read_req.bits.addr:=  Cat(io.icache_req.bits.addr(31,ICACHE_OFFSET_WIDTH),0.U(ICACHE_OFFSET_WIDTH.W))
  io.io_read_resp.ready:=true.B
  io.icache_resp.bits.addr:=io.icache_req.bits.addr

  //debug
  io.icache_debug.cache_we:=cache_we
  io.icache_debug.icache_req.valid:=io.icache_req.valid
  io.icache_debug.icache_req.bits.addr:=io.icache_req.bits.addr
  io.icache_debug.state:=state
  io.icache_debug.hit_cache:=hit_cache
  io.icache_debug.cache_read_tag:=cache_read_tag
}
