# AGVN Player (agvn.io) - MIT License (see LICENSE). Loaded by mkxp-z after ruby_classic_wrap.rb, before the game's
# scripts.
# What RGSS's Ruby (1.8, 1.9 on Windows) did that mkxp-z's Ruby 3.1 on the phone does otherwise, beside
# ruby_classic_wrap.rb's:
# - nil.to_s, true.to_s and false.to_s give a new string, which a script may change. Ruby 3 gives one frozen string,
#   and clone keeps it frozen: "text.to_s.clone" then gsub! stopped with "can't modify frozen String" at the status
#   screen (Rei and the Floating City's "拡張選択1", 08/11/2026);
# - File.basename, dirname, extname and split read "\" as a folder separator, as Ruby on Windows does. A path a game
#   wrote for Windows, "System\RGSS301.dll" from Game.ini, kept its folder in its base name, so a script's
#   File.basename(lib) == "RGSS301.dll" did not hold and it left Graphics::PlaneSpeedUp undefined.
class NilClass
  def to_s = String.new(encoding: Encoding::US_ASCII)
end

class TrueClass
  def to_s = String.new('true', encoding: Encoding::US_ASCII)
end

class FalseClass
  def to_s = String.new('false', encoding: Encoding::US_ASCII)
end

module AgvnWindowsPaths
  def self.windows(path)
    path.is_a?(String) && path.include?('\\') ? path.tr('\\', '/') : path
  end
end

class << File
  %i[basename dirname extname split].each do |name|
    alias_method :"agvn_ruby19_#{name}", name
    define_method(name) { |path, *rest| __send__(:"agvn_ruby19_#{name}", AgvnWindowsPaths.windows(path), *rest) }
  end
end
