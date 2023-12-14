# Shanks Touché / Golden Retrieval - IR Project 

This is the Golden Retrieval group's repository for the homework of the Search Engines course at DEI - Department of Information Engineering, University of Padua, Italy.

## Team Member
- Filippo Berno - filippo.berno@studenti.unipd.it
- Andrea Cassetta - andrea.cassetta@studenti.unipd.it
- Alice Codogno - alice.codogno@studenti.unipd.it
- Enrico Vicentini - enrico.vicentini.1@studenti.unipd.it
- Alberto Piva - alberto.piva.8@studenti.unipd.it

## **Most of the development was done by all members of the group working simultaneously. The reason the commits are not distributed equally is because the members pushing to the repository were always the same for convenience.**

## How to run
The program take in input from command line three parameters. The first and second parameters are mandatory while the third indicates which run you want to obtain.

1 Download the project directory

2 Compile with Maven with the following lines

```
$ mvn clean package
```

3 Go to the directory java

4 Run the program with java machine 

```
$ java -cp target/Shanks-Touche-1.00-jar-with-dependencies.jar it.unipd.dei.se.ShanksTouche inputDatasetPath outpurDirPath [n_run]
```
```
Required parameters:
    - inputDatasetPath  = contains the path the the index directory; 
    - outpurDirPath = contains the path to the run file; 
```
```   
Optional parameters:    
    - n_run (OPTIONAL) contains the number of RUN to be execute that can be:
        1 -> RUN 1 - Re-Ranking approach based on maxNdcg and maxRecall (default) 
        2 -> RUN 2 - Re-Ranking approach based on maxNdcg and maxRecall 
        3 -> RUN 3 - maxNdcg using LMD similarity 
        4 -> RUN 4 - maxNdcg using MULTI similarity 
        5 -> RUN 5 - maxRecall using MULTI similarity

```

## Requirements

### N.B. Wordnet dict folder to be inserted in the RESOURCES directory of the program before running the maven code listed above
- Wordnet 3.0+ https://wordnet.princeton.edu/download/current-version for linux based systems Wordnet 2.1 for windows based systems 
- Dataset from https://zenodo.org/record/3734893#.YIwtB7UzaUk
- Maven 3.6.3
- Lucene 8.8.1
- JDK 11
