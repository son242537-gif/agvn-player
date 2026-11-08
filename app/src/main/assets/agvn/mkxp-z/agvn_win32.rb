# AGVN Player (agvn.io) - MIT License (see LICENSE). Loaded by mkxp-z after win32_wrap.rb, before the game's scripts.
# win32_wrap.rb (mkxp-z) stands in for some Windows functions scripts call through Win32API. Here, whatever name a
# game gives a DLL finds that stand-in, or none, and never stops the game:
# - "user32", "User32", "user32.dll" and "USER32.DLL" are one DLL, and "FindWindow" also finds "FindWindowA", as on
#   Windows. win32_wrap.rb took the name for a Ruby constant: "System/KGL2.klib", a DLL a light script ships, stopped
#   the game with "wrong constant name System/KGL2.klib" (report of 04/11/2026);
# - a function nothing stands in for returns 0, as before, and goes once to tim-tep.txt (AGVN_RGSS_FILES_LOG) as
#   "Win32API giả: <dll>!<function>". A light script then draws no light: the game plays, without its lights;
# - a stand-in that fails returns 0 as well, and says so on the same line;
# - a DLL of the game itself raises instead, as on Windows when it cannot load, where the script rescues that
#   (agvn_win32_rescue.rb): the script then does without it, its own way;
# - a buffer a script made with DL (agvn_dl.rb) reaches the stand-ins as the string it is;
# - Call, call's other name, takes the stand-in too: it went to mkxp-z's own call, which has no function here;
# - a Win32API the script froze calls as any other (win32_wrap.rb's call wrote on it).
module AgvnWin32
  LOG_LINES = 40
  # What is known of a function once: its stand-in, and that mkxp-z cannot load it (a script asking every frame)
  KNOWN = 1024
  @logged = {}
  @impls = {}
  @no_native = {}

  class << self
    # The DLL's name as a Ruby constant: "System/KGL2.klib" -> "KGL2", "user32.dll" -> "User32", "3d-fx" -> "Dll_3d_fx"
    def const_name(dll)
      base = File.basename(dll.to_s.b.tr('\\', '/'))
      base = base.sub(/\..*\z/m, '') unless base.start_with?('.')
      base = base.gsub(/[^A-Za-z0-9_]/, '_')
      base = "Dll_#{base}" unless base.match?(/\A[A-Za-z]/)
      base[0] = base[0].upcase
      base
    end

    def key(dll, func)
      "#{dll.to_s.b}!#{func.to_s.b}"
    end

    # The class standing in for {func} of {dll} (win32_wrap.rb's or AGVN's), or nil
    def impl(dll, func)
      k = key(dll, func)
      return @impls[k] if @impls.key?(k)
      found = lookup(dll, func)
      @impls[k] = found if @impls.size < KNOWN
      found
    end

    # Whether something stands in for {dll} (a module of its name in Win32API_Impl), as for user32 or CAMouse
    def stood_in_dll?(dll)
      find(Win32API_Impl, const_name(dll)).is_a?(Module)
    rescue StandardError
      false
    end

    def no_native?(dll, func)
      @no_native.key?(key(dll, func))
    end

    def no_native(dll, func)
      @no_native[key(dll, func)] = true if @no_native.size < KNOWN
    end

    # Once for each function that returned 0 for want of a stand-in, or because its stand-in failed ({error}), or
    # that raised ({note})
    def stood_in(dll, func, error = nil, note = nil)
      k = key(dll, func)
      return if @logged.key?(k) || @logged.size >= LOG_LINES
      @logged[k] = true
      line = 'Win32API giả: '.b + k
      line += " (#{error.class}: #{error.message})".b if error
      line += " (#{note})".b if note
      System.puts(line)
      file = ENV['AGVN_RGSS_FILES_LOG']
      File.open(file, 'ab') { |f| f.puts(line) } if file && !file.empty?
    rescue StandardError
      nil # never in the game's way
    end

    # A game's own DLL the phone cannot load raises as on Windows where the script rescues it (agvn_win32_rescue.rb)
    def unloadable(dll, func, from)
      return unless from && defined?(AgvnRescue) && AgvnRescue.game_dll?(dll)
      return unless AgvnRescue.guarded?(from.path, from.lineno)
      stood_in(dll, func, nil, 'không nạp được, script dùng cách dự phòng của nó')
      raise "LoadLibrary: #{dll}"
    end

    # What a stand-in writes into: the memory behind a DL pointer or its address (agvn_dl.rb), else the argument
    def memory(arg)
      return arg unless defined?(DL) && DL.respond_to?(:agvn_memory)
      return arg.agvn_memory if arg.respond_to?(:agvn_memory)
      arg.is_a?(Integer) ? DL.agvn_memory(arg) || arg : arg
    end

    private

    def lookup(dll, func)
      mod = find(Win32API_Impl, const_name(dll))
      return nil unless mod.is_a?(Module)
      f = func.to_s.b
      [f, "#{f}A", f.chomp('A'), f.chomp('W')].each do |name|
        c = name.empty? ? nil : find(mod, name)
        return c if c.is_a?(Class)
      end
      nil
    rescue StandardError
      nil
    end

    # {mod}'s constant named {name} in any case, without taking {name} for a constant name
    def find(mod, name)
      c = mod.constants.find { |k| k.to_s.b.casecmp?(name) }
      c ? mod.const_get(c) : nil
    end
  end
end

class Win32API
  alias_method :agvn_win32_wrap_call, :call

  def initialize(dll, func, *args)
    @dll = dll
    @func = func
    @called = false
    unless System.is_windows? && NATIVE_ON_WINDOWS
      impl = AgvnWin32.impl(dll, func)
      begin
        @mkxp_wrap_impl = impl.new if impl
      rescue StandardError
        @mkxp_wrap_impl = nil
      end
      return if @mkxp_wrap_impl
    end
    @mkxp_native_available = false
    unless AgvnWin32.no_native?(dll, func)
      begin
        mkxp_native_initialize(dll, func, *args)
        @mkxp_native_available = true
        return
      rescue StandardError
        AgvnWin32.no_native(dll, func) # a DLL only Windows has: call returns 0
      end
    end
    AgvnWin32.unloadable(dll, func, caller_locations(1, 1)&.first)
  end

  def call(*args)
    if @mkxp_wrap_impl
      begin
        r = @mkxp_wrap_impl.call(args.map { |a| AgvnWin32.memory(a) })
        return r if r.is_a?(Integer) || r.is_a?(String)
        return r ? 1 : 0 # what Windows returns is a number
      rescue StandardError => e
        AgvnWin32.stood_in(@dll, @func, e)
        return 0
      end
    end
    return agvn_win32_wrap_call(*args) if @mkxp_native_available
    # 0, as win32_wrap.rb's call, without its note on the object: a script may freeze it (Win32API.new(...).freeze),
    # and "can't modify frozen MiniFFI" hid the game's own error (Rei and the Floating City, 08/11/2026)
    AgvnWin32.stood_in(@dll, @func)
    0
  end

  alias_method :Call, :call
end
