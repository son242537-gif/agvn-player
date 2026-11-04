# AGVN Player (agvn.io) - MIT License (see LICENSE). Loaded by mkxp-z before the game's scripts.
# DL, Ruby 1.9's library for C memory, which RGSS3 (VX Ace) has and mkxp-z's Ruby 3.1 does not: DL.malloc, DL.free and
# DL::CPtr, as far as scripts use them to hand Win32API a buffer by its address. Cidiomar's keyboard input (in Lona
# RPG, Data/Scripts/Frames/RVscript/1000_Hime_AllKey.rb) reads every key with
# GetKeyboardState(DL::CPtr.new(DL.malloc(256), 256).to_i). Without DL that script stopped at that line,
# "uninitialized constant Input::DL", before its Input.update and press?, and the game's own keys (:MZ_LINK, :S1…)
# did nothing.
# The memory is a Ruby string. Its address is a number only Win32API's stand-ins know: agvn_win32.rb hands them the
# string. No real DLL ever gets it, as none runs on the phone. A byte reads as C's char, -128 to 127, as DL read it.
unless defined?(DL)
  module DL
    # Far from the numbers scripts pass otherwise: handles, key codes, sizes
    BASE = 0x7AC0_0000
    @memory = {}
    @next = BASE

    class DLError < StandardError; end

    class << self
      # No C library opens on the phone: as DL says of one it cannot open
      def dlopen(lib, *_flags)
        raise DLError, "can't load #{lib}"
      end

      def malloc(size)
        size = [size.to_i, 0].max
        addr = @next
        @next += (size + 16) & ~15
        @memory[addr] = ("\0" * size).b
        addr
      end

      def free(addr)
        @memory.delete(addr.to_i)
        nil
      end

      # The memory at {addr}: one DL.malloc gave, or nil
      def agvn_memory(addr)
        @memory[addr]
      end
    end

    class CPtr
      attr_reader :size

      def self.malloc(size, _free = nil)
        new(DL.malloc(size), size)
      end

      def initialize(addr, size = 0, _free = nil)
        @addr = addr.to_i
        @size = size.to_i
        @memory = DL.agvn_memory(@addr) || ("\0" * [@size, 0].max).b
      end

      def to_i
        @addr
      end

      def null?
        @addr.zero?
      end

      def agvn_memory
        @memory
      end

      def [](offset, len = nil)
        return @memory.byteslice(offset, len) if len
        b = @memory.getbyte(offset) || 0
        b > 127 ? b - 256 : b
      end

      def []=(offset, *rest)
        bytes = rest.size == 1 ? [rest[0].to_i & 0xFF].pack('C') : rest[1].to_s.b.byteslice(0, rest[0].to_i)
        bytes.bytesize.times do |i|
          @memory.setbyte(offset + i, bytes.getbyte(i)) if offset + i < @memory.bytesize
        end
        rest.last
      end

      # The bytes up to the first 0, or {len} of them
      def to_s(len = nil)
        len ? @memory.byteslice(0, len) : @memory.b[/\A[^\0]*/n]
      end

      def to_str(len = nil)
        @memory.byteslice(0, len || @memory.bytesize)
      end

      def ==(other)
        other.is_a?(CPtr) && other.to_i == @addr
      end
      alias eql? ==

      def hash
        @addr.hash
      end
    end

    NULL = CPtr.new(0)
  end
end
