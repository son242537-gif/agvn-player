# AGVN Player (agvn.io) - MIT License (see LICENSE).
# Checks app/src/main/assets/agvn/mkxp-z/agvn_ruby19_cvars.rb on a PC with Ruby 3.1 or newer (mkxp-z has Ruby 3.1.3):
#   ruby scripts/agvn/mkxp-z/test-agvn-ruby19-cvars.rb
# The scripts run as mkxp-z runs them: eval(source, nil, "NNN:name") one after another, from $RGSS_SCRIPTS[i][3].
# 7 Girls War's "プチスロット" (14/11/2026) stopped with "class variable access from toplevel" at its line 112.
require 'tmpdir'

ASSETS = File.expand_path('../../../app/src/main/assets/agvn/mkxp-z', __dir__)
$failures = 0

def check(what, ok)
  puts "#{ok ? 'ok  ' : 'FAIL'} #{what}"
  $failures += 1 unless ok
end

module RPG
  class SE
    attr_reader :name
    def initialize(name) = @name = name
    def play = "played #{@name}"
  end
end

SLOT = <<~RUBY
  module CAO; module PSLOT; SOUND_REEL_STOP = 'Decision3'; end; end
  # CACAO's slot machine, as 7 Girls War has it
  class << Sound
    @@reel_stop = RPG::SE.new(CAO::PSLOT::SOUND_REEL_STOP)   # line 4

    def play_reel_stop
      @@reel_stop.play
    end
  end
  $agvn_slot_line = __LINE__
RUBY
NESTED = <<~RUBY
  module Foo
    class << self
      @@q = 3
      def q = @@q
    end
  end
RUBY
MAIN = <<~RUBY
  class << self # a method of main, from a class variable
    @@greeting = 'xin chào'
    def agvn_greeting = @@greeting
  end
RUBY
UNSURE = "class << Sound\n@@flat = 1\nend\n" # its inside is not indented: which "end" closes it is not sure
PLAIN = "class << Sound\n  def plain = 1\nend\n" # no @@: nothing to change

check('without the fix, Ruby 3 stops as on the phone',
      (begin; eval(SLOT.sub('class << Sound', "module Sound; end\nclass << Sound"), nil, 'x'); false
       rescue RuntimeError => e; e.message.include?('class variable access from toplevel'); end))

Dir.mktmpdir do |dir|
  ENV['AGVN_RGSS_FILES_LOG'] = log = File.join(dir, 'tim-tep.txt')
  $RGSS_SCRIPTS = [[1, 'Sound', '', "module Sound\nend\n"], [2, 'プチスロット', '', SLOT.dup], [3, 'Foo', '', NESTED.dup],
                   [4, 'Main', '', MAIN.dup], [5, 'Unsure', '', UNSURE.dup], [6, 'Plain', '', PLAIN.dup]]
  load File.join(ASSETS, 'agvn_ruby19_cvars.rb')
  results = $RGSS_SCRIPTS.each_with_index.map do |s, i|
    eval(s[3], nil, format('%03d:%s', i, s[1]))
    nil
  rescue RuntimeError => e
    e.message
  end

  check('the slot machine script loads', results[1].nil?)
  check('its sound plays as on the PC', Sound.play_reel_stop == 'played Decision3')
  check('the variable is Object\'s, as in Ruby 1.9', Object.class_variable_get(:@@reel_stop).is_a?(RPG::SE))
  check('every line keeps its number', $agvn_slot_line == SLOT.lines.size)
  check('a class << self at the top level works too', results[3].nil? && agvn_greeting == 'xin chào')
  check('a module\'s own class << self is left as it was (its variable stays the module\'s)',
        $RGSS_SCRIPTS[2][3] == NESTED && Foo.q == 3 && Foo.class_variable_defined?(:@@q))
  check('a block whose end is not sure is left as it was', $RGSS_SCRIPTS[4][3] == UNSURE)
  check('a block without @@ is left as it was', $RGSS_SCRIPTS[5][3] == PLAIN && results[5].nil?)
  lines = File.read(log, encoding: 'UTF-8').lines
  check('tim-tep.txt names the script and the line, once each',
        lines.size == 2 && lines[0].include?("script 1 'プチスロット', dòng 3") && lines[1].include?("script 3 'Main', dòng 1"))
end

check('a block with a heredoc ending at column 0 is left as it was',
      AgvnRuby19Cvars.fix("class << Sound\n  @@x = <<EOS\nEOS\nend\n").nil?)
check('Windows line ends stay', AgvnRuby19Cvars.fix("class << Sound\r\n  @@x = 1\r\nend\r\n")&.first ==
      "class Object; class << Sound\r\n  @@x = 1\r\nend; end\r\n")

puts $failures.zero? ? 'all passed' : "#{$failures} FAILED"
exit($failures.zero? ? 0 : 1)
