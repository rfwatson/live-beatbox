BeatBox {
  var <>basepath;
  
  var w, <layout, <scope, <server, beats, nchannels, seq, data, recbutton;
  var onsetListener, statusText, thresholdText, lagText, scope, <lowerStatus;
  var fftbuf, recbuf, synth, isListening, isRecording, isJamming, recStart;
  var jambutton;
  
  *new { |basepath|
    ^super.new.init(basepath);
  }
  
  init { |aBasePath|
    basepath = (aBasePath ? "/l/cm2/coursework/task3") ++ "/";
        
    w = SCWindow("Beatbox Beta 1", Rect(200, 700, 520, 558), resizable: false);

    // keyboard shorts    
    w.view.keyDownAction_{ |...args| 
      var shift = args[2];
      var code = args.last;

      code.switch(
        36, { // enter
            seq.setCurrentHit;  
        },
        13, { // backspace
            seq.clearCurrentHit;
        },
        49, {
          // move to seq
          seq.mainbutton.value_((seq.mainbutton.value + 1) % 2).doAction;
        },
        122, {
          if(recbutton.enabled) {
            recbutton.value_((recbutton.value + 1) % 2).doAction;  
          }
        },
        120, {
          this.preview;
        },
        99, {
          if(jambutton.enabled) {
            jambutton.value_((jambutton.value + 1) % 2).doAction;  
          }
        },
        123, {
          // move to seq
          if(seq.xfadeval > 0.0) {
            seq.xfadeslid.value_(seq.xfadeval - 0.1).doAction;
          }
        },
        124, {
          // move to seq
          if(seq.xfadeval < 1.0) {
            seq.xfadeslid.value_(seq.xfadeval + 0.1).doAction;
          }
        },
        126, { // move to seq
          seq.currentChannel = (seq.currentChannel - 1) % seq.numChannels;
          seq.selects[seq.currentChannel].value_(1).doAction;
        },
        125, { // move to seq
          seq.currentChannel = (seq.currentChannel + 1) % seq.numChannels;          
          seq.selects[seq.currentChannel].value_(1).doAction;
        }
      )
    };
    
    layout      = FlowLayout(w.view.bounds);
    w.view.decorator = layout;
    
    nchannels   = 8;
    
    server      = Server.local;
    server.doWhenBooted {
      recbuf    = Buffer.alloc(server, server.sampleRate);  
      fftbuf    = Buffer.alloc(server, 512);
    };
    
    isListening = false;
    isRecording = false;
    isJamming   = false;
    beats       = Array.newClear(nchannels);
    
    File.use(basepath ++ "data.sc", "r") { |file|
      data = file.readAllString.interpret;
    };
        
    this.initDisplay;
  }
  
  initDisplay {
    recbutton = SCButton(w, Rect(5, 5, 100, 25))
      .states_([
        [ "Record (F1)", Color.black, Color.green ],
        [ "Stop (F1)", Color.white, Color.red ],
      ])
      .action_{ |button| this.switch(button) }
      .keyDownAction_{ nil };
      
    SCButton(w, Rect(5, 5, 100, 25))
      .states_([
        [ "Preview (F2)", Color.black, Color.green ],
      ])
      .action_{ |button| this.preview(button) }
      .keyDownAction_{ nil };
      
    jambutton = SCButton(w, Rect(5, 5, 100, 25))
      .states_([
        [ "Jam (F3)", Color.black, Color.green ],
        [ "Stop (F1)", Color.white, Color.red ],
      ])
      .action_{ |button| this.jam(button.value) }
      .keyDownAction_{ nil };

      
    layout.nextLine;
    layout.nextLine;
      
    SCStaticText(w, Rect(5, 5, 200, 25))
      .string_("Threshold")
      .font_(Font("Helvetica-Bold", 12));


    SCStaticText(w, Rect(200, 5, 30, 25))
      .string_("Lag")
      .font_(Font("Helvetica-Bold", 12));
      
    layout.nextLine;
      
    SCSlider(w, Rect(5, 5, 100, 25))
      .step_(0.1)
      .value_(0.3)
      .action_{ |slider| 
        var value = slider.value.clip(0.1, 1.0);
        synth.set(\threshold, value);
        thresholdText.string_(value.asString);
      }
      .keyDownAction_{ nil };
      
    thresholdText = SCStaticText(w, Rect(5, 5, 95, 25))
      .string_("0.3");
      
    SCSlider(w, Rect(200, 5, 100, 25))
      .step_(0.05)
      .value_(0.1)
      .action_{ |slider| 
        var value = slider.value.clip(0.1, 1.0);
        synth.set(\lag, value);
        lagText.string_(value.asString);
      }
      .keyDownAction_{ nil };
      
    lagText = SCStaticText(w, Rect(5, 5, 20, 25))
      .string_("0.1");
      
    layout.nextLine;
    
    statusText = SCStaticText(w, Rect(5, 5, 510, 25))
      .background_(Color.grey);
    
    layout.nextLine;
    
    scope = SCUserView(w, Rect(5, 5, 510, 150))
      .relativeOrigin_(true)
      .background_(Color.black)
      .drawFunc_({ this.drawWaveform });
      
    layout.nextLine;
    
    lowerStatus = SCStaticText(w, Rect(5, 5, 510, 25))
      .background_(Color.grey);
      
    layout.nextLine;
    
    seq = Sequencer(w, Rect(5, 5, 510, 300), server, beats, this);
    
    w.onClose = { scope.free };
    w.front;
  }
  
  switch { |button|
    if(button.value == 1) { 
      jambutton.enabled = false;
      this.listen;
    } { 
      this.silenceDetected;
      this.ignore;
      jambutton.enabled = true;
    }
  }
  
  jam { |value| // 0 is on, 1 is off: relates to SCButton value
    if(value == 1) {
      recbutton.enabled = false;
      isJamming = true;
      if(seq.playing.not) {
        seq.play
      };
      this.listen(amp: 0.0);
    } {
      isJamming = false;
      recbutton.enabled = true;
      jambutton.value = 0;
      this.ignore;
    }
  }
  
  preview { 
    seq.previewCurrentChannel;
  }
  
  onsetDetected {    
    Post << "Onset!" << $\n;
    if(isJamming) {
      {
        seq.setCurrentHit;
      }.defer;
    } {
      if(isRecording.not) {
        isRecording = true;
        synth.set(\t_resetRecord, 1);
        recStart = SystemClock.seconds;
        { statusText.background_(Color.red).stringColor_(Color.white).background_(Color.red).string_("RECORDING") }.defer;
      }      
    }
  }
  
  silenceDetected {
    var dursamps, sndfile, signal, beat;
    Post << "Silence!" << $\n;
        
    if(isRecording) {
      synth.run(false);
      { 
        statusText.background_(Color.green).stringColor_(Color.black).string_("Analysing...");
      }.defer;
      dursamps = ((SystemClock.seconds - recStart) * server.sampleRate).asInteger;
      recbuf.loadToFloatArray(0, dursamps) { |floatdata|
        // check received from server OK - sometimes fails
        if(floatdata.isEmpty.not) {
          signal = Signal.newFrom(floatdata);
          signal.normalize;
          
          beats[seq.currentChannel] = Beat.newFromSignal(signal, server);
          beats[seq.currentChannel].analyse { |beat| 
            this.findNearest(beat);
            {
              // allow new recording to begin only after completely finsihed
              scope.refresh;
              isRecording = false;
              statusText.background_(Color.grey).stringColor_(Color.black).string_("");
              this.updateLowerStatus;
              recbutton.value_(0).doAction;
            }.defer;
          }
        }
      }
    }
  }
  
  listen { |amp=1.0|
    var msg, action;
    
    if(isListening.not) { // start listening to input
      synth = Synth(\beatboxlistener, [\out, 0, \in, 0, \fftbuf, fftbuf, \recbuf, recbuf, \amp, amp]);
      
      onsetListener = OSCresponderNode(nil, '/tr') { |time, responder, msg|
        action = msg[3];
        action.asInteger.switch(
          1, { this.onsetDetected },
          2, { this.silenceDetected }
        );
      }.add;
      
      statusText.background_(Color.grey).stringColor_(Color.black).string_("Waiting for input...");
      isListening = true;
    }
  }
  
  ignore {
    if(isListening) {
      var wasRecording = isRecording;
      this.silenceDetected;
      synth.free;
      onsetListener.remove;
      
      if(wasRecording) {
        statusText.background_(Color.green).stringColor_(Color.black).string_("Analysing...");        
      } {
        statusText.background_(Color.grey).stringColor_(Color.black).string_("");  
      };
      
      isListening = false;
    }
  }
  
  // TODO move to Beat.sc
  findNearest { |beat|
    var p, total;
    var nearest = 10e10; 
    var neighbour;

    data.getPairs.clump(2).do { |pair|
      var fname, features;
      # fname, features = pair;
      total = 0.0;
      features.size.do { |i|
        p = (beat.features[i] - features[i]).squared;
        total = total + p;
      };

      if(total < nearest) {
        neighbour = fname;
        nearest = total;
      };
    };
    
    if(neighbour.isNil) { // FIX sometimes a strange bug causes a lot of nan's to be produced
      "There was an error analysing this sound. Try another".postln;
    } {
      beat.nearest = neighbour;
    }    
  }
  
  refresh {
    scope.refresh
  }
  
  updateLowerStatus {
    var beat = seq.beats[seq.currentChannel];
    beat !? {
      beat.nearestPath !? {
        lowerStatus.string_(beat.nearestPath.basename);  
        ^this;
      }
    };
    
    lowerStatus.string_("");                
  }
  
  drawWaveform {
    // why is SCSoundFileView so frustrating?
    var sig, maximums, x2, y1, y2, p1, p2;
    var beat = beats[seq.currentChannel];
        
    beat !? {
      p1 = 0 @ 75;
      
      if(beat.sample.notNil) {
        sig = (beat.sample * (seq.xfadeval)) +.s (beat.signal * (1 - seq.xfadeval));
      } {
        sig = beat.signal;
      };

      maximums = sig.clump(sig.size / 510).collect{ |window|
        var posmax, negmax;
        posmax = window.maxItem;
        negmax = window.minItem;

        if(posmax.abs > negmax.abs)
          { posmax } { negmax }
      };

      Pen.use {
        Color(0.3, 1, 0.3).set;
        maximums.do { |max, x1|
          p2 = x1 @ (75 - (75 * max));
          Pen.moveTo(p1);
          Pen.lineTo(p2);
          Pen.stroke;
          
          p1 = p2;
        }
      }
    }
  }
}
