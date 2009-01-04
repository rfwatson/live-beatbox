// This is the script I used to batch analyse a directory of audio samples.

// It scans a directory, analyses each suitable audio
// sample found and stores the results in the environment variable
// ~output.

// This output should be saved in a file called "data.sc" in the root directory.

// At present sound samples are limited to 1 second in length..


// TODO improve this system!


(
var maxFrames = 44100;
var path      = Document.current.dir ++ "/samples/".standardizePath; // edit to your path
var files     = (path ++ "*.wav").pathMatch;  
~output       = (); 

Routine {
  files.do { |fname|
    var len;
  
    try {
      Post << "Processing " << fname << $\n;

      Beat.newFromPath(fname, s) { |beat|
        beat.analyse { |beat| 
          ~output[fname] = beat.features;
        };
      };    
    } { |error|
      error.errorString.postln;
    };
    
    1.wait
  };  
}.play;  
)
