SynthDef(\beatboxlistener) { |out=0, in=0, amp=1.0, threshold=0.3, lag=0.1, t_resetRecord=0.0, fftbuf, recbuf|
  var input, chain, onsets, peak;

  input   = SoundIn.ar(in);
  chain   = FFT(fftbuf, input);
  onsets  = Onsets.kr(chain, threshold, \rcomplex);
  peak    = PeakFollower.ar(input, 0.01).lag(lag);  

  RecordBuf.ar(input, bufnum: recbuf, trigger: t_resetRecord); 

  SendTrig.kr(onsets, value: 1);
  SendTrig.ar((peak < 0.0002), value: 2);
  
  Out.ar(out, input * amp);
}.load(s);

// TODO Envelope recognition? This is pretty good at selecting timbres but often the envelopes
// of the two sounds (perceptually just as important??) are very different.
SynthDef(\beatboxanalyzer) { |out=0, fftbuf, bufnum, cbus1, cbus2, cbus3, t_reset=0.0|
  var sig, inbuf, chain, coeffs, centroid, zcr;
  
  sig       = PlayBuf.ar(numChannels: 1, bufnum:bufnum);
  chain     = FFT(fftbuf, sig, wintype: 1); // Hann windowing
  coeffs    = MFCC.kr(chain, 20);
  centroid  = SpecCentroid.kr(chain).linlin(100, 10000, 0, 1.5, \minmax); // perhaps a slight weighting
  zcr       = ZeroCrossing.ar(sig).linlin(100, 10000, 0, 1.5, \minmax); // towards these two
  
  Out.kr(cbus1, AverageOutput.kr(coeffs, trig: t_reset));
  Out.kr(cbus2, AverageOutput.kr(centroid, trig: t_reset));
  Out.kr(cbus3, A2K.kr(AverageOutput.kr(zcr, trig: t_reset)));
}.load(s);


SynthDef(\beatboxplayer) { |out=0, bufnum1, bufnum2, crossfade=0.0, mul=1.0|
  var p1    = PlayBuf.ar(numChannels: 1, loop: 0, bufnum: bufnum1);
  var p2    = PlayBuf.ar(numChannels: 1, loop: 0, bufnum: bufnum2);
  var amp1  = 1 - crossfade;
  var amp2  = crossfade;
  var dur   = BufDur.kr(bufnum1);
  var env   = EnvGen.kr(Env([1,1,0], [dur * 0.9, dur]));

  Out.ar(out,
    mul * Pan2.ar(
      (((p1 * 0.5) * (env * amp1)) + ((p2 * 0.5) * (env * amp2)));
    )
  );
}.load(s);
