paper
=====
This repo contains code used for the paper "Object-Model Transfer in the General Video Game Domain" appearing in AIIDE 2016: http://nn.cs.utexas.edu/?braylan:aiide16.

gvgai
=====
The version of GVG-AI used here was cloned from https://github.com/EssexUniversityMCTS/gvga on September 27, 2015.

dependencies
=====
The only dependency is my neural nets repo: https://github.com/Praznat/NeuralNets.
You also need Java, this was built using v1.8.

instructions
=====
`LaunchObjectModeler.java` contains the main function, the accuracy experiment, the exploration experiment, and various other tests. `Player.java`, `ObjectInstance.java`, `ObjectClassModel.java`, and `RichMemoryManager.java` contain the bulk of the code for interfacing with and collecting data from GVG-AI game episodes. Most of the transfer learning methods are in `Transfer.java` and `EnsembleClassModel.java`.
