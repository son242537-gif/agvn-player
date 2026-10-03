# AGVN Player (agvn.io.vn) - MIT License (see LICENSE). Loaded by mkxp-z before the game's own scripts.
# How many frames the game runs per second, for the "Chạy nhẹ" HUD and the slow-game check ("Tự sửa lỗi"):
# every 2 s, "<frames per second> <the game's frame rate>" goes to the file AGVN_RGSS_FPS_FILE names.
# A game that later redefines Graphics.update without calling the old one only stops the reports.
if ENV['AGVN_RGSS_FPS_FILE']
  class << Graphics
    alias_method :agvn_fps_update, :update

    def update(*args)
      agvn_fps_update(*args)
      begin
        now = Process.clock_gettime(Process::CLOCK_MONOTONIC)
        @agvn_fps_since ||= now
        @agvn_fps_frames = (@agvn_fps_frames || 0) + 1
        if now - @agvn_fps_since >= 2
          File.write(ENV['AGVN_RGSS_FPS_FILE'], format('%.1f %d', @agvn_fps_frames / (now - @agvn_fps_since), frame_rate))
          @agvn_fps_frames = 0
          @agvn_fps_since = now
        end
      rescue StandardError
        nil # never in the game's way
      end
    end
  end
end
