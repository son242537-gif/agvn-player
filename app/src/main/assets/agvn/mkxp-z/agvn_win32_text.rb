# AGVN Player (agvn.io) - MIT License (see LICENSE). Loaded by mkxp-z after agvn_win32_screen.rb, before the game's
# scripts.
# MultiByteToWideChar and WideCharToMultiByte (kernel32): text between a code page and UTF-16, as Windows converts it.
# They returned 0 for want of a stand-in (Rei and the Floating City, 08/11/2026), so a script got empty text. Code page
# 0 (the PC's own, CP_ACP) is a Japanese PC's, 932, as RPG Maker's games mostly were; 65001 is UTF-8. With no room
# given (0), each returns the size the text needs, in characters or bytes, as on Windows.
module AgvnText
  class << self
    def encoding(cp)
      case cp.to_i
      when 0, 1, 2, 3, 932 then Encoding::Windows_31J
      when 65001 then Encoding::UTF_8
      else Encoding.find("CP#{cp.to_i}")
      end
    rescue ArgumentError
      Encoding::Windows_31J
    end

    # {count} units of {unit} bytes of {src}; up to and with the first 0 when {count} is negative
    def take(src, count, unit)
      bytes = src.is_a?(String) ? src.b : ''.b
      return bytes.byteslice(0, count.to_i * unit) || ''.b if count.to_i >= 0
      zero = "\0".b * unit
      at = (0...bytes.bytesize).step(unit).find { |i| bytes.byteslice(i, unit) == zero }
      at ? bytes.byteslice(0, at + unit) : bytes + zero
    end

    # {out} into {buf} when it has {room} (in units of {unit}); the units written, or needed when {room} is 0
    def give(out, buf, room, unit)
      units = out.bytesize / unit
      return units if room.to_i <= 0
      return 0 if units > room.to_i || !AgvnScreen.fill(buf, out).positive?
      units
    end
  end
end

module Win32API_Impl
  module Kernel32
    # (code page, flags, text, its bytes, UTF-16 buffer, its room in characters)
    class MultiByteToWideChar
      def call(args)
        text = AgvnText.take(args[2], args[3], 1).force_encoding(AgvnText.encoding(args[0]))
        out = text.encode(Encoding::UTF_16LE, invalid: :replace, undef: :replace).b
        AgvnText.give(out, args[4], args[5], 2)
      end
    end

    # (code page, flags, UTF-16 text, its characters, buffer, its room in bytes, default char, default used)
    class WideCharToMultiByte
      def call(args)
        text = AgvnText.take(args[2], args[3], 2).force_encoding(Encoding::UTF_16LE)
        out = text.encode(AgvnText.encoding(args[0]), invalid: :replace, undef: :replace, replace: '?').b
        AgvnText.give(out, args[4], args[5], 1)
      end
    end
  end
end
