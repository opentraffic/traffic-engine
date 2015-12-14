# traffic-engine
Java Library for generating structured traffic speed statistics. OSM street extracts and GPS points go in. Anonymized OSM-linked speed samples come out.

See traffic-engine-app for a packaged web application for collecting and visualizing traffic statistics.

## build

    $ mvn clean package
    
###For local testing you might want to `install` into ~/.mvn/ so that traffic-engine-app builds:
    $ mvn install
    
## run

