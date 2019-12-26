package com.getbouncer.cardscan.base.ml.models

/**
 * handle the main loop and completion loop
 */

/*
   need to consider two things:
   1. executor for main loop (hook into camerax API)
      a. consider abstracting the `analyze` method of the camera
      b. pass the analyze method into some class to use as an executor
      c. generalize this out to part 2

   2. Create a looper which will execute an `analyze` method given an executor and an analyze method
      a. should be able to specify our own "analyzer"
 */

class AnalyzerLoop {
    var running = false


}
