package org.deeplearning4j.text.sentenceiterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.token.type.Sentence;
import org.cleartk.util.cr.FilesCollectionReader;
import org.deeplearning4j.text.annotator.SentenceAnnotator;
import org.deeplearning4j.text.annotator.TokenizerAnnotator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Iterates over and returns sentences
 * based on the passed in analysis engine
 * @author Adam Gibson
 *
 */
public class UimaSentenceIterator extends BaseSentenceIterator {

    protected volatile CollectionReader reader;
    protected volatile AnalysisEngine engine;
    protected volatile Iterator<String> sentences;
    protected String path;
    private static Logger log = LoggerFactory.getLogger(UimaSentenceIterator.class);

    public UimaSentenceIterator(SentencePreProcessor preProcessor,String path, AnalysisEngine engine) {
        super(preProcessor);
        this.path = path;
        try {
            this.reader  = FilesCollectionReader.getCollectionReader(path);
        } catch (ResourceInitializationException e) {
            throw new RuntimeException(e);
        }
        this.engine = engine;
    }

    public UimaSentenceIterator(String path, AnalysisEngine engine) {
        this(null,path,engine);
    }

    @Override
    public synchronized  String nextSentence() {
        if(sentences == null || !sentences.hasNext()) {
            try {
                if(getReader().hasNext()) {
                    CAS cas = engine.newCAS();

                    synchronized (reader) {
                        try {
                            getReader().getNext(cas);
                        }catch(Exception e) {
                            log.warn("Done iterating returning an empty string");
                            return "";
                        }

                    }
                    synchronized (engine) {
                        engine.process(cas);

                    }

                    List<String> list = new ArrayList<>();
                    for(Sentence sentence : JCasUtil.select(cas.getJCas(), Sentence.class)) {
                        list.add(sentence.getCoveredText());
                    }


                    sentences = list.iterator();
                    //needs to be next cas
                    while(!sentences.hasNext()) {
                        //sentence is empty; go to another cas
                        if(reader.hasNext()) {
                            cas.reset();
                            getReader().getNext(cas);
                            engine.process(cas);
                            for(Sentence sentence : JCasUtil.select(cas.getJCas(), Sentence.class)) {
                                list.add(sentence.getCoveredText());
                            }
                            sentences = list.iterator();
                        }
                        else
                            return null;
                    }


                    String ret = sentences.next();
                    if(this.getPreProcessor() != null)
                        ret = this.getPreProcessor().preProcess(ret);
                    return ret;
                }

                return null;

            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }
        else {
            String ret = sentences.next();
            if(this.getPreProcessor() != null)
                ret = this.getPreProcessor().preProcess(ret);
            return ret;
        }



    }

    /**
     * Creates a uima sentence iterator with the given path
     * @param path the path to the root directory or file to read from
     * @return the uima sentence iterator for the given root dir or file
     * @throws Exception
     */
    public static SentenceIterator createWithPath(String path) throws Exception {
        return new UimaSentenceIterator(path,AnalysisEngineFactory.createEngine(AnalysisEngineFactory.createEngineDescription(TokenizerAnnotator.getDescription(), SentenceAnnotator.getDescription())));
    }


    @Override
    public synchronized boolean hasNext() {
        try {
            return getReader().hasNext() || sentences != null && sentences.hasNext();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private synchronized  CollectionReader getReader() {
        return reader;
    }


    @Override
    public void reset() {
        try {
            this.reader  = FilesCollectionReader.getCollectionReader(path);
        } catch (ResourceInitializationException e) {
            throw new RuntimeException(e);
        }
    }



}
