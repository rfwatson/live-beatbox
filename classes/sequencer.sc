Sequencer {
  var w, view, channels, buttons, <buttonvals, <selects, indicators, beatbox, routine, tempoBox, startedTime;
  var <xfadeslid, <xfadeval, <>currentChannel, <beats, server, <mainbutton, <playing;
  
  *new { |w, bounds, server, beats, beatbox, nchannels=8|
    ^super.new.init(w, bounds, server, beats, beatbox, nchannels);
  }
          
  init { |aWindow, bounds, aServer, someBeats, aBeatBox, nchannels|
    w               = aWindow;
    channels        = 0.0 ! 16 ! nchannels;
    indicators      = Array.newClear(16);
    playing         = false;
    beats           = someBeats;
    server          = aServer;
    beatbox         = aBeatBox;
    buttons         = List[] ! nchannels; // the buttons
    buttonvals      = List[] ! nchannels; // mirror image of their values: so I can reach them without {}.defer
    selects         = List[];
    currentChannel  = 0;
    xfadeval        = 0.0;
    
    this.initDisplay(bounds);
    this.initRoutine;
  }
  
  initRoutine {
    routine = Routine {
      var n = 0, hit, mul;
      
      inf.do {
        hit = n % 16;
        
        indicators.do { |light, col|
          if(col == hit) { 
            { light.value = 1 }.defer(0.2)
          } {
            { light.value = 0 }.defer(0.2)
          } 
        };
        
        server.bind {
          channels.size.do { |channel|
            if(buttonvals[channel][hit] > 0) {
              buttonvals[channel][hit].switch(
                1, { mul = 0.3 },
                2, { mul = 0.7 },
                3, { mul = 1.0 }
              );
              beats[channel].play(xfadeval, mul)
            }
          }
        };

        n = n + 1;
        (((60 / tempoBox.value)) / 4).wait
      }
    }
  }
  
  initDisplay { |bounds|        
    var cbar, tbar;
    
    view      = SCVLayoutView(w, bounds);
    view.background_(Color.grey);
    
    cbar = SCHLayoutView(view, Rect(5, 5, 510, 25));
    
    mainbutton = SCButton(cbar, 100 @ 20)
      .states_([
        [ "Play", Color.black, Color.green ],
        [ "Stop", Color.white, Color.red ]
      ])
      .action_{ |button|
        button.value.switch(
          0, { this.stop },
          1, { this.play }
        )
      }
      .keyDownAction_{ nil };

    SCStaticText(cbar, 30 @ 20)
      .string_("");    
    
    SCStaticText(cbar, 30 @ 20)
      .string_("BPM");
    
    SCSlider(cbar, 60 @ 20)
      .action_{ |slid|
        var spec = [80, 180, \linear, 1].asSpec;
        tempoBox.value = spec.map(slid.value);
      }
      .keyDownAction_{ nil }
      .value_(0.5);
      
    tempoBox = SCNumberBox(cbar, Rect(5, 5, 60, 20))
      .value_(130);
      
    SCStaticText(cbar, 30 @ 20)
      .string_("");    
    
    SCStaticText(cbar, 42 @ 20)
      .string_("XFader");

    xfadeslid = SCSlider(cbar, 60 @ 20)
      .action_{ |slider| 
        xfadeval = slider.value;
        beatbox.refresh;
      }
      .keyDownAction_{ nil };

    tbar = SCHLayoutView(view, Rect(5, 5, 510, 20));
    
    SCStaticText(tbar, Rect(5, 5, 60, 20));
    
    16.do { |n|
      indicators[n] = SCButton(tbar, Rect(5, 5, 20, 20))
        .states_([
          [ "", Color.grey, Color.grey ],
          [ "", Color.black, Color.green ]
        ])
        .enabled_(false)
    };
    
    channels.size.do { |channel|
      var button;
      var hbox = SCHLayoutView(view, Rect(5, 5, 380, 20));
      hbox.background_(Color(0.4, 0.4, 0.4));
      
      button = SCButton(hbox, Rect(5, 5, 60, 20))
        .states_([
          [ "Select", Color.black, Color.grey ],
          [ "Select", Color.black, Color.yellow ]
        ])
        .action_{ |thisButton|
          view.children[2..channels.size+1].do { |hbox, n|
            var anotherButton = hbox.children.first;
            if(thisButton == anotherButton) {
              currentChannel = n;
              thisButton.value = 1;
              beatbox.scope.refresh;
              beatbox.updateLowerStatus;
             } {
              anotherButton.value = 0;
            }
          }
        }
        .keyDownAction_{ nil };
        
      if(channel == 0)
        { button.value = 1 } { button.value = 0 };
        
      selects.add(button);
      
      16.do { |n|
        var button;
        button = SCButton(hbox, Rect(5, 5, 20, 20))
          .states_([
            [ "", Color.white, Color.grey ],
            [ "x", Color.black, Color.yellow ],
            [ "x", Color.black, Color(1.0, 0.55, 0.0) ],
            [ "X", Color.white, Color.red ]
          ])
          .action_{ |button|
            buttonvals[channel][n] = button.value;
          }
          .keyDownAction_{ nil };
          
        buttons[channel].add(button);
        buttonvals[channel].add(0);
      }
    }
  }
  
  play {
    if(playing.not) {
      startedTime = SystemClock.seconds;
      routine.play;
      playing = true;
      mainbutton.value = 1;
    }
  }
  
  stop {
    if(playing) {
      routine.stop;
      this.initRoutine;
      beatbox.jam(0);
      indicators.do { |light| { light.value = 0 }.defer(0.2) };
      playing = false;      
      mainbutton.value = 0;
    }
  }
  
  setCurrentHit {
    var currentHit = this.currentHit;
    beats[currentChannel].play;
    buttonvals[currentChannel][currentHit] = 2;
    buttons[currentChannel][currentHit].value = 2;
  }
  
  clearCurrentHit {
    var currentHit = this.currentHit;
    buttonvals[currentChannel][currentHit] = 0;
    buttons[currentChannel][currentHit].value = 0;
  }
  
  currentHit {
    var durplaying, bardur, sdur, thisbar, currhit;

    if(playing) {
      durplaying = SystemClock.seconds - startedTime;
      bardur = (60 / tempoBox.value) * 4;
      sdur = bardur / 16;
      thisbar = durplaying % bardur;
      currhit = ((thisbar / sdur).asInteger - 1) % 16; 
    
      ^currhit      
    } {
      ^0
    }
  }
  
  allButtons {
    ^view.children[1..9].collect{ |hview| 
      hview.children[1..16]
    };
  }
  
  numChannels {
    ^channels.size;
  }
  
  previewCurrentChannel {
    beats[currentChannel].play(xfadeval, 0.7)
  }
}
