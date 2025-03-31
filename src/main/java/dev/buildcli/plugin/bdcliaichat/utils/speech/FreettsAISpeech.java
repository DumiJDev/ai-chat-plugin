package dev.buildcli.plugin.bdcliaichat.utils.speech;

import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;

public class FreettsAISpeech implements AISpeech {

  private final VoiceManager voiceManager;

  public FreettsAISpeech() {
    System.setProperty("freetts.voices", "com.sun.speech.freetts.en.us.cmu_us_kal.KevinVoiceDirectory");
    voiceManager = VoiceManager.getInstance();
  }

  @Override
  public void speak(String text) {
    Voice voice = voiceManager.getVoice("kevin16");
    voice.allocate();
    voice.setRate(150);
    voice.setVolume(3);
    voice.setPitch(100);
    voice.speak(text);
    voice.deallocate();
  }
}
