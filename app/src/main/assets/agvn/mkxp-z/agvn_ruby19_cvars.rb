# AGVN Player (agvn.io) - MIT License (see LICENSE). Loaded by mkxp-z before the game's scripts. mkxp-z has decoded them
# into $RGSS_SCRIPTS by then (each [id, name, packed, source]) and runs each one's source from there.
# A class variable (@@x) in a "class << X" block that no class or module encloses: RGSS's Ruby 1.9 took it as Object's,
# Ruby 3 stops with "class variable access from toplevel". 7 Girls War's "プチスロット" (CACAO's slot machine,
# 14/11/2026) has "class << Sound" with "@@reel_stop = RPG::SE.new(...)", and the game stopped before its title.
# Such a block becomes "class Object; class << X" ... "end; end" on the same lines: the variable is Object's again, as
# on the PC, and every line keeps its number. Only those blocks change, which Ruby 3 cannot run. A block whose end
# cannot be told for sure from the indentation, or a script that would not compile after, is left as it was. Each
# change goes once to tim-tep.txt (AGVN_RGSS_FILES_LOG).
module AgvnRuby19Cvars
  # "class << Sound", "class << self", "class << ::A::B", with a comment or none
  SINGLETON = /\Aclass\s*<<\s*(self|(?:::)?[A-Z]\w*(?:::[A-Z]\w*)*)\s*(#.*)?\z/
  CONTAINER = /\A([ \t]*)(?:class|module)\b(?!\s*<<)/
  ONE_LINE = /;\s*end\s*(#.*)?\z/
  CVAR = /@@[A-Za-z_]/

  module_function

  # {source} with its top-level "class << X" blocks that use @@ inside Object's class body, and the numbers of their
  # first lines; nil when none was changed
  def fix(source)
    return nil unless source.include?('@@') && source.include?('<<')
    lines = source.lines
    open = [] # indentation of each class or module body open at this line
    changed = []
    comment = false
    i = 0
    while i < lines.size
      text = lines[i].chomp
      if comment
        comment = false if text.start_with?('=end')
      elsif text.start_with?('=begin')
        comment = true
      elsif text.start_with?('__END__')
        break
      elsif open.empty? && (m = SINGLETON.match(text)) && (j = closing(lines, i))
        if lines[i + 1...j].any? { |l| CVAR.match?(l) }
          target = m[1] == 'self' ? 'TOPLEVEL_BINDING.receiver' : m[1]
          lines[i] = "class Object; class << #{target}#{m[2] ? " #{m[2]}" : ''}#{eol(lines[i])}"
          lines[j] = lines[j].sub(/\Aend\b/, 'end; end')
          changed << i + 1
        end
        i = j
      elsif (m = CONTAINER.match(text)) && !ONE_LINE.match?(text)
        open.push(m[1])
      elsif !open.empty? && text.start_with?("#{open.last}end") && text.match?(/\A[ \t]*end\b/)
        open.pop
      end
      i += 1
    end
    return nil if changed.empty?
    fixed = lines.join
    RubyVM::InstructionSequence.compile(fixed)
    [fixed, changed]
  rescue SyntaxError, StandardError
    nil
  end

  # The line of the "end" of the block opened at column 0 on line {i}: the first "end" at column 0 after it, with
  # every line between indented, blank, or a comment; nil when another line starts at column 0 first
  def closing(lines, i)
    comment = false
    (i + 1...lines.size).each do |j|
      text = lines[j].chomp
      if comment
        comment = false if text.start_with?('=end')
        next
      end
      next comment = true if text.start_with?('=begin')
      return j if text.match?(/\Aend\b/)
      return nil unless text.empty? || text.match?(/\A[ \t#]/)
    end
    nil
  end

  def eol(line)
    line[/\r?\n\z/] || ''
  end

  # Every script of the game, in $RGSS_SCRIPTS
  def apply(scripts)
    return unless scripts.is_a?(Array)
    scripts.each_with_index do |script, n|
      next unless script.is_a?(Array) && script[3].is_a?(String)
      fixed, at = fix(script[3])
      next unless fixed
      script[3] = fixed
      at.each do |line|
        log("Ruby 1.9: @@ trong \"class << ...\" (script #{n} '".b + script[1].to_s.b +
            "', dòng #{line}) đọc như trên PC".b)
      end
    end
  rescue StandardError
    nil # never in the game's way
  end

  def log(line)
    file = ENV['AGVN_RGSS_FILES_LOG']
    File.open(file, 'ab') { |f| f.puts(line) } if file && !file.empty?
  rescue StandardError
    nil
  end
end

AgvnRuby19Cvars.apply($RGSS_SCRIPTS)
