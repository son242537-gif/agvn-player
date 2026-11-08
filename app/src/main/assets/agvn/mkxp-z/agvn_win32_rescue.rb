# AGVN Player (agvn.io) - MIT License (see LICENSE). Loaded by mkxp-z after agvn_win32.rb, before the game's scripts.
# On Windows, Win32API.new raises when a DLL cannot load, and a script may count on it: it tries a DLL of its own and,
# in a rescue, does the work in Ruby instead. Lona RPG's palette changer does so with
# Data/PaletteChanger/BitmapChanger_Optimization.dll: given stand-ins that return 0, it took the DLL for working, and
# no colour would change. So a DLL of the game (a file in its folder) that the phone cannot load raises, as on
# Windows, when the line that asks for it is inside a rescue that takes a RuntimeError. Anywhere else it gets the
# stand-in, as before: a script that does not expect the error would stop (Khas's light library asks for KGL2.klib
# outside any rescue). The rescue is read from the script's own code, compiled (never run) once per script.
module AgvnRescue
  # What a rescue must take for Win32API's RuntimeError to reach it
  TAKES = %w[StandardError RuntimeError Exception].freeze
  HEADER = 'YARVInstructionSequence/SimpleDataFormat'.freeze
  @seqs = {}

  class << self
    # Whether {dll} is a file of the game, not one of Windows or RGSS's own
    def game_dll?(dll)
      name = dll.to_s.tr('\\', '/')
      return false if name.empty? || File.basename(name).match?(/\Argss\d/i)
      File.file?(name) || File.file?("#{name}.dll")
    rescue StandardError
      false
    end

    # Whether line {line} of the script {path} runs inside a rescue that takes a RuntimeError, or one of {also}
    # (DL.dlopen's DL::DLError: "DLError")
    def guarded?(path, line, also = [])
      seq = seq(path.to_s)
      seq ? covered?(seq, line, TAKES + also) : false
    rescue StandardError, ScriptError
      false
    end

    private

    def seq(path)
      return @seqs[path] if @seqs.key?(path)
      src = source(path)
      @seqs[path] = src && RubyVM::InstructionSequence.compile(src, path, path, 1).to_a
    rescue StandardError, ScriptError
      @seqs[path] = nil
    end

    # The script's code: a file of the game ("Data/Scripts/x.rb"), or a script of Scripts.rvdata2 ("012:Name")
    def source(path)
      n = path[/\A(\d+):/, 1] || path[/\A\{(\d+)\}\z/, 1] || path[/\ASection(\d+)\z/, 1]
      if n
        s = $RGSS_SCRIPTS.is_a?(Array) ? $RGSS_SCRIPTS[n.to_i] : nil
        return s.is_a?(Array) && s[3].is_a?(String) ? s[3].dup.force_encoding(Encoding::UTF_8) : nil
      end
      return nil unless File.file?(path)
      File.binread(path).force_encoding(Encoding::UTF_8).delete_prefix("﻿")
    end

    def covered?(top, line, takes)
      each_seq(top) do |s|
        s[12].each do |c|
          next unless c[0] == :rescue && takes_runtime?(c[1], takes)
          lines = region(s[13], c[2], c[3])
          return true if lines && lines.include?(line)
        end
      end
      false
    end

    # The lines between labels {from} and {to}, with those of the blocks given there
    def region(body, from, to)
      lines = []
      inside = false
      body.each do |i|
        inside ||= i == from
        break if i == to
        next unless inside
        if i.is_a?(Integer)
          lines << i
        elsif i.is_a?(Array)
          i.each { |o| each_seq(o) { |s| s[13].each { |x| lines << x if x.is_a?(Integer) } } }
        end
      end
      lines.empty? ? nil : (lines.min..lines.max)
    end

    def takes_runtime?(rescue_seq, takes)
      return false unless rescue_seq.is_a?(Array) && rescue_seq[0] == HEADER
      rescue_seq[13].any? do |i|
        next false unless i.is_a?(Array)
        name = case i[0]
               when :putobject then i[1].is_a?(Module) ? i[1].name : nil
               when :getconstant then i[1].to_s
               when :opt_getconstant_path then Array(i[1]).last.to_s
               end
        takes.include?(name)
      end
    end

    # {seq} and every code compiled within it: methods, blocks, classes, rescues
    def each_seq(seq, &block)
      return unless seq.is_a?(Array) && seq[0] == HEADER
      yield seq
      seq[12].each { |c| each_seq(c[1], &block) }
      seq[13].each { |i| i.each { |o| each_seq(o, &block) } if i.is_a?(Array) }
    end
  end
end
