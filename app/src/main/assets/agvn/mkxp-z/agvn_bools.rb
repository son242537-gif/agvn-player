# AGVN Player (agvn.io) - MIT License (see LICENSE). Loaded by mkxp-z before the game's own scripts.
# The yes/no settings of RGSS's objects (Sprite#visible=, Window#active=, Font#bold= and the like) take any value, as
# RGSS on Windows does: false and nil are no, anything else is yes. mkxp-z takes true, false and nil only and raises
# "Argument 0: Expected bool" for the rest: Lona RPG's light library (KHAS) gave a sprite's visible= another value as
# its first map came up, and the game stopped there (log of 07/11/2026). A setter mkxp-z lacks is left out.
module AgvnBools
  SETTERS = {
    Sprite: %i[visible= mirror=], Plane: %i[visible=], Viewport: %i[visible=], Tilemap: %i[visible=],
    Window: %i[visible= active= pause= arrows_visible=], Font: %i[bold= italic= outline= shadow=]
  }.freeze
  CLASS_SETTERS = {
    Font: %i[default_bold= default_italic= default_outline= default_shadow=], Graphics: %i[fullscreen=]
  }.freeze

  class << self
    # Each of {target}'s {names} passes on its value as true or false, through an alias of mkxp-z's own as RGSS scripts
    # chain theirs: a game that aliases the setter later and calls its alias wraps this one (prepend would loop there)
    def wrap(target, names)
      names.each do |name|
        next unless target.method_defined?(name)
        own = :"agvn_bool_#{name}"
        next if target.method_defined?(own) # loaded twice
        target.send(:alias_method, own, name)
        target.send(:define_method, name) { |value| __send__(own, value ? true : false) }
      end
    end
  end
end

begin
  AgvnBools::SETTERS.each { |name, setters| AgvnBools.wrap(Object.const_get(name), setters) if Object.const_defined?(name) }
  AgvnBools::CLASS_SETTERS.each do |name, setters|
    AgvnBools.wrap(Object.const_get(name).singleton_class, setters) if Object.const_defined?(name)
  end
rescue StandardError
  nil # a Ruby without them runs as before
end
