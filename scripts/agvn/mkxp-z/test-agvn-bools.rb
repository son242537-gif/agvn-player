# AGVN Player (agvn.io) - MIT License (see LICENSE).
# Checks app/src/main/assets/agvn/mkxp-z/agvn_bools.rb on a PC with Ruby 3.1 or newer (mkxp-z has Ruby 3.1.3):
#   ruby scripts/agvn/mkxp-z/test-agvn-bools.rb
# The RGSS classes here stand in for mkxp-z's: their yes/no setters take true, false and nil only, as mkxp-z's
# rb_bool_arg does. A second run without the script must stop as Lona RPG did on the phone (log of 07/11/2026):
# "Argument 0: Expected bool" in a light library's sprite.visible = with another value.
require 'rbconfig'

ASSETS = File.expand_path('../../../app/src/main/assets/agvn/mkxp-z', __dir__)
SELF_TEST = File.expand_path(__FILE__)
UPSTREAM = ENV['AGVN_TEST_UPSTREAM'] == '1'
$failures = 0

def check(what, ok)
  puts "#{ok ? 'ok  ' : 'FAIL'} #{what}"
  $failures += 1 unless ok
end

# mkxp-z's DEF_PROP_B: true, false or nil, else TypeError
module StrictBool
  def self.define(klass, *names)
    names.each do |name|
      klass.define_method("#{name}=") do |v|
        raise TypeError, 'Argument 0: Expected bool' unless [true, false, nil].include?(v)
        instance_variable_set("@#{name}", v ? true : false)
      end
      klass.define_method(name) { instance_variable_get("@#{name}") }
    end
  end
end

class Sprite; end
class Window; end
class Font; end
module Graphics; end
StrictBool.define(Sprite, :visible, :mirror)
StrictBool.define(Window, :visible, :active, :pause)
StrictBool.define(Font, :bold)
StrictBool.define(Font.singleton_class, :default_bold)

# A light library's sprite, in the shape KHAS's takes: a subclass that sets visible from a value of its own
class LightSprite < Sprite
  def refresh(intensity)
    self.visible = intensity # an Integer, as a light's strength can be
  end
end

if UPSTREAM
  e = (LightSprite.new.refresh(3) rescue $!)
  check('without agvn_bools.rb: "Argument 0: Expected bool", as on the phone', e.is_a?(TypeError))
  exit($failures.zero? ? 0 : 1)
end

load File.join(ASSETS, 'agvn_bools.rb')
s = LightSprite.new
s.refresh(3)
check('a light library\'s visible = 3 is yes', s.visible == true)
s.refresh(nil)
check('nil is no', s.visible == false)
s.refresh(0)
check('0 is yes, as in Ruby and RGSS', s.visible == true)
s.mirror = 'yes'
check('mirror = a string', s.mirror == true)
w = Window.new
w.active = :menu
w.pause = false
check('Window#active= and pause=', w.active == true && w.pause == false)
f = Font.new
f.bold = 1
Font.default_bold = []
check('Font#bold= and Font.default_bold=', f.bold == true && Font.default_bold == true)
check('true and false as they are', (s.visible = false) == false && s.visible == false)

# a game that aliases visible= and puts its own over it, as RGSS scripts chain theirs: its own sees the value as on
# Windows, mkxp-z's gets true or false, and nothing loops
class Sprite
  alias_method :game_visible=, :visible=
  def visible=(v)
    @seen = v
    self.game_visible = v
  end
  attr_reader :seen
end
t = Sprite.new
t.visible = 2
check("a game's own visible= sees 2, as on Windows; the sprite is visible", t.seen == 2 && t.visible == true)
load File.join(ASSETS, 'agvn_bools.rb')
t.visible = nil
check('loaded twice: still one wrap', t.visible == false)

upstream = IO.popen({ 'AGVN_TEST_UPSTREAM' => '1' }, [RbConfig.ruby, SELF_TEST], err: %i[child out], &:read)
puts upstream
check('without agvn_bools.rb the game stops as on the phone', $?.success?)
puts $failures.zero? ? 'all passed' : "#{$failures} failed"
exit($failures.zero? ? 0 : 1)
