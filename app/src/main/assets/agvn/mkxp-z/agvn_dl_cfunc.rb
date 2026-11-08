# AGVN Player (agvn.io) - MIT License (see LICENSE). Loaded by mkxp-z after agvn_dl.rb, before the game's scripts.
# The rest of DL a script calls a Windows function with: DL.dlopen and DL::Handle (a DLL), handle['Function'] (its
# address), DL::CFunc.new(address, DL::TYPE_LONG).call([...]) and DL::CPtr[string] (a C string's address).
# Rei and the Floating City's "解像度上限突破" (VX Ace, 08/11/2026) does so as it loads, with no rescue: DL.dlopen
# raised "can't load user32", then DL::CFunc and DL::CPtr[] were missing, and the game stopped before its title.
# A function called so reaches the same stand-in Win32API.new gives (win32_wrap.rb, agvn_win32*.rb), with a DL buffer
# as the string it is; one without a stand-in returns 0 and goes once to tim-tep.txt, as there. A DLL of the game the
# phone cannot load raises as Win32API.new does: only where the script rescues it (agvn_win32_rescue.rb).
if defined?(DL) && DL.respond_to?(:agvn_memory)
  module DL
    TYPE_VOID, TYPE_VOIDP, TYPE_CHAR, TYPE_SHORT, TYPE_INT, TYPE_LONG, TYPE_LONG_LONG, TYPE_FLOAT, TYPE_DOUBLE =
      0, 1, 2, 3, 4, 5, 6, 7, 8
    SIZEOF_VOIDP = SIZEOF_INT = SIZEOF_LONG = SIZEOF_FLOAT = 4 # RGSS's Windows is 32-bit
    SIZEOF_CHAR, SIZEOF_SHORT, SIZEOF_LONG_LONG, SIZEOF_DOUBLE = 1, 2, 8, 8
    RTLD_LAZY, RTLD_NOW, RTLD_GLOBAL = 1, 2, 256
    # Function addresses, far from DL.malloc's memory and from the numbers scripts pass otherwise
    FUNCTIONS = 0x7E00_0000
    @functions = {}
    @addresses = {}

    class << self
      def dlopen(lib, *_flags)
        from = caller_locations(1, 1)&.first
        if from && defined?(AgvnRescue) && AgvnRescue.game_dll?(lib) && !AgvnWin32.stood_in_dll?(lib) &&
           AgvnRescue.guarded?(from.path, from.lineno, %w[DLError])
          AgvnWin32.stood_in(lib, '(DL.dlopen)', nil, 'không nạp được, script dùng cách dự phòng của nó')
          raise DLError, "can't load #{lib}"
        end
        Handle.new(lib)
      end

      # The address of {func} of {dll}, the same each time
      def agvn_address(dll, func)
        key = AgvnWin32.key(dll, func)
        @addresses[key] ||= begin
          addr = FUNCTIONS + 16 * @functions.size
          @functions[addr] = [dll.to_s, func.to_s]
          addr
        end
      end

      # [dll, function] at {addr}, or nil
      def agvn_function(addr)
        @functions[addr.to_i]
      end
    end

    class Handle
      def initialize(lib = nil, *_flags)
        @lib = lib.to_s
      end

      def sym(name)
        DL.agvn_address(@lib, name)
      end
      alias [] sym

      def to_i
        DL.agvn_address(@lib, '')
      end

      def close
        0
      end
    end

    class CFunc
      attr_reader :ctype, :calltype

      def initialize(addr, type = TYPE_VOID, name = nil, calltype = :cdecl)
        @addr = addr.to_i
        @ctype = type
        @calltype = calltype
        @dll, func = DL.agvn_function(@addr)
        @name = (name || func).to_s
      end

      def name
        @name
      end

      def to_i
        @addr
      end
      alias ptr to_i

      # The stand-in's result, as Win32API's call gives it; 0 for none
      def call(args = [])
        @stub ||= (c = AgvnWin32.impl(@dll, @name)) && c.new
        unless @stub
          AgvnWin32.stood_in(@dll, @name)
          return 0
        end
        r = @stub.call(Array(args).map { |a| AgvnWin32.memory(a) })
        r.is_a?(Integer) ? r : r.is_a?(Float) ? r : (r ? 1 : 0)
      rescue StandardError => e
        AgvnWin32.stood_in(@dll, @name, e)
        0
      end
      alias [] call
    end

    class CPtr
      # A C string's address: the bytes of {obj} and a 0, in memory a stand-in reads as the string; an address or
      # CPtr as it is
      def self.to_ptr(obj)
        return obj if obj.is_a?(CPtr)
        return new(obj.to_i) if obj.is_a?(Integer)
        bytes = "#{obj.to_s.b}\0".b
        addr = DL.malloc(bytes.bytesize)
        DL.agvn_memory(addr).replace(bytes)
        new(addr, bytes.bytesize)
      end

      class << self
        alias [] to_ptr
      end
    end
  end
end
