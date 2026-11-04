# AGVN Player (agvn.io) - MIT License (see LICENSE). Loaded by mkxp-z after agvn_win32_screen.rb (AgvnScreen), before
# the game's scripts.
# GetPrivateProfileString and GetPrivateProfileInt (kernel32) read a key of an .ini file as Windows does: the value,
# or the default the script gives when the file, its section or its key is missing. They returned 0 and left the
# buffer empty, so a script got "" where a PC gives the default: Fullscreen++ read its window ratio as 0 ("fit the
# screen", which divides by the screen's size) instead of its default 1, from a Game.ini without its section.
# A name without a folder is in Windows' own folder on a PC, where a game's .ini never is: the default, as there.
# Writing a key (WritePrivateProfileString) is still not done: the game's files stay as they are.
module AgvnIni
  BOM = "\xEF\xBB\xBF".b.freeze

  class << self
    # The value of {key} in [{section}] of the file {path} names, as bytes; nil when there is none
    def value(path, section, key)
      path = AgvnScreen.text(path)
      section = AgvnScreen.text(section)
      key = AgvnScreen.text(key)
      return nil unless path && section && key && path.tr('\\', '/').include?('/')
      lines(path.tr('\\', '/')).each_with_object([false]) do |line, inside|
        if line.start_with?('[')
          inside[0] = line[1..].sub(/\].*\z/m, '').strip.casecmp?(section)
          next
        end
        name, eq, val = line.partition('=')
        return unquote(val.strip) if inside[0] && !eq.empty? && name.strip.casecmp?(key.strip)
      end
      nil
    rescue StandardError
      nil
    end

    private

    def lines(path)
      text = File.binread(path)
      text = text[BOM.bytesize..] if text.start_with?(BOM)
      text.split(/\r?\n|\r/).map(&:strip).reject { |l| l.empty? || l.start_with?(';') }
    end

    # Windows drops the quotes around a value
    def unquote(val)
      val.size >= 2 && %w[" '].include?(val[0]) && val[-1] == val[0] ? val[1..-2] : val
    end
  end
end

module Win32API_Impl
  module Kernel32
    # (section, key, default, buffer, buffer size, file): the bytes copied, the buffer ending with a 0
    class GetPrivateProfileString
      def call(args)
        size = args[4].to_i
        buf = args[3]
        return 0 if size <= 0 || !buf.is_a?(String) || buf.empty?
        found = AgvnIni.value(args[5], args[0], args[1]) || AgvnScreen.text(args[2]) || ''.b
        n = [found.bytesize, size - 1, buf.bytesize - 1].min
        AgvnScreen.fill(buf, found.byteslice(0, n) + "\0".b)
        n
      end
    end

    # (section, key, default, file): the number the key starts with, 0 if none, the default when it is missing
    class GetPrivateProfileInt
      def call(args)
        found = AgvnIni.value(args[3], args[0], args[1])
        return args[2].to_i if found.nil?
        found.strip[/\A[-+]?\d+/].to_i
      end
    end
  end
end
