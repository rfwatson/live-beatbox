Beat {
  classvar <maxFrames;
  var <signal, <nframes, <server, sigbuf, <sample, sampbuf, <path, <features, fftbuf, bus, <nearest, <nearestPath;
  
  *initClass {
    maxFrames = 44100; // 1 second of audio, which is more than enough.
  }
  
  *newFromSignal { |signal, server, donefunc|
    ^super.new.initFromSignal(signal, server, donefunc);
  }
  
  *newFromPath { |path, server, donefunc|
    ^super.new.initFromPath(path, server, donefunc);
  }
  
  initFromSignal { |aSignal, aServer, donefunc|
    signal      = aSignal;
    nframes     = signal.size;
    server      = aServer;
    
    // we always pass in a mono signal so no need to check here
    sigbuf      = Buffer.alloc(server, nframes, 1);
    sigbuf.loadCollection(signal, action: {
      donefunc.(this)
    });
  }
  
  initFromPath { |aPath, aServer, donefunc|
    var sndfile, rawsignal;
    path        = aPath;
    server      = aServer;
    sndfile     = SoundFile.openRead(path);
    
    if(sndfile.numFrames > maxFrames) {
      Error("Sound file too large.").throw;
    } {
      rawsignal   = Signal.newClear(sndfile.numFrames * sndfile.numChannels);
      sndfile.readData(rawsignal);
    
      if(sndfile.numChannels == 1) {
        signal = rawsignal;
      } {
        signal = rawsignal.clump(sndfile.numChannels).flop[0]; // channel 0
      };
    
      sigbuf = Buffer.loadCollection(server, signal, 1, action: {
        donefunc.(this)
      });
    };
  }
  
  play { |xfade=0.0, mul=1.0|
    var synth;

    // why isnt doneAction working for me?
    synth = Synth(\beatboxplayer, [\bufnum1, sigbuf, \bufnum2, sampbuf, \crossfade, xfade, \mul, mul]);
    SystemClock.sched(3.0, { synth.free });
  }

  analyse { |donefunc|
    var duration, synth;

    duration  = sigbuf.numFrames / server.sampleRate;
    
    bus         = Bus.control(server, 22);  // 20 MFCCs, 1 Spectral Centroid, 1 ZCR
    fftbuf      = Buffer.alloc(server, 1024, 1);

    server.makeBundle(1.0) {
      synth = Synth(\beatboxanalyzer, [\bufnum, sigbuf, \fftbuf, fftbuf, \cbus1, bus.index, \cbus2, bus.index + 20, \cbus3, bus.index + 21]);
    };
  
    server.makeBundle(1.0 + duration, {
      bus.getn(22) { |theFeatures|
        features = theFeatures;
        donefunc.(this);
      };
    });
      
    // 2 seconds later, free the synth, fft-buffer and the control bus.
    SystemClock.sched(3.0 + duration) {
      synth.free;
      fftbuf.free;
      bus.free;
    };
  }
  
  nearest_{ |path|
    var sndfile, rawsignal, len;

    nearestPath = path;
    sndfile     = SoundFile.openRead(path);
    rawsignal   = Signal.newClear(sndfile.numFrames * sndfile.numChannels);
    sndfile.readData(rawsignal);
    sample      = rawsignal.clump(2).flop[0];
    
    sampbuf = Buffer.readChannel(server, path, channels: [0], action: { |buf|
      sampbuf = buf;
    });
  }
}
