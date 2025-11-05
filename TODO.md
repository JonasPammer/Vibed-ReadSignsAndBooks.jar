
* the output folder should keep a file denoting failed-to-read region files. 
  subsequent runs should only mention in one single log that errors for these will not be printed anymore (though they are still tried)
  if a region that was bad goes good, it should be removed.
  the file in the output folder should use the input folder as they key to differentiate between worlds.