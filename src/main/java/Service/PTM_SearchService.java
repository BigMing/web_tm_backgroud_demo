package Service;

import Dao.PTM_Sentence;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.SortBuilders;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Created by sunjm on 2016/3/31.
 */
public class PTM_SearchService {
    public JsonService jsonService = new JsonService();
    public CalculateSimilarityService calculateSimilarityService = new CalculateSimilarityService();


    //public ClientService clientService;
    public Client client;

    public PTM_SearchService(){
        try {
            client = TransportClient.builder().build()
                    .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost"),9300)
                    );
        }catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public Map<PTM_Sentence,Float> Search_Source_Sentence(String searchString){
        //List<PTM_Sentence> sentenceList = new ArrayList<PTM_Sentence>();
        Map<PTM_Sentence,Float> sentenceMap = new HashMap<PTM_Sentence, Float>();
        SearchResponse response = client.prepareSearch("web_tm").setTypes("ptm_sentence")
                .setQuery(QueryBuilders.multiMatchQuery(searchString,"source_sentence"))
                .setSize(5)
                .addSort(SortBuilders.scoreSort())
                .setMinScore((float) 0.6)
                .execute().actionGet();
        SearchHits hits = response.getHits();
        System.out.println("查询到的句子数：" + hits.getTotalHits());
        for (SearchHit hit : hits){
            int sid = (Integer)hit.getSource().get("sid");
            int article_id = (Integer)hit.getSource().get("article_id");
            String source_lang = (String)hit.getSource().get("source_lang");
            String target_lang = (String)hit.getSource().get("target_lang");
            String source_sentence = (String)hit.getSource().get("source_sentence");
            String target_sentence = (String)hit.getSource().get("target_sentence");
            String translator = (String)hit.getSource().get("translator");
            String provenience = (String)hit.getSource().get("provenience");
            Double ranking = (Double)hit.getSource().get("ranking");
            String remark = (String)hit.getSource().get("remark");
            sentenceMap.put(new PTM_Sentence(sid,article_id,source_lang,target_lang,
                    source_sentence,target_sentence,translator,provenience,ranking,remark),
                    hit.getScore());
        }
        return sentenceMap;
    }

    public Map<PTM_Sentence,Float> wordOrderSearch(String searchString){
        Map<PTM_Sentence,Float> sentenceMap = Search_Source_Sentence(searchString);
        Map<PTM_Sentence,Float> tempMap = new HashMap<PTM_Sentence, Float>();
        for (PTM_Sentence sentence : sentenceMap.keySet()){
            float similarity = calculateSimilarityService.getWordOrderSimilarity(
                    searchString,sentence.getSource_sentence()
            );
            tempMap.put(sentence,similarity);
        }
        return sortSearchReasult(tempMap);
    }

    public Map<PTM_Sentence,Float> simanticSearch(String searchString){
        Map<PTM_Sentence,Float> sentenceMap = Search_Source_Sentence(searchString);
        Map<PTM_Sentence,Float> tempMap = new HashMap<PTM_Sentence, Float>();
        for (PTM_Sentence ptm_sentence : sentenceMap.keySet()){
            float similarity = calculateSimilarityService.getSimanticSimilarity(
                    searchString,ptm_sentence.getSource_lang()
            );
            tempMap.put(ptm_sentence,similarity);
        }
        return sortSearchReasult(tempMap);
    }

    public Map<PTM_Sentence,Float> sortSearchReasult(Map<PTM_Sentence,Float> sentenceMap){
        List<Map.Entry<PTM_Sentence,Float>> infoIds =
                new ArrayList<Map.Entry<PTM_Sentence,Float>>(sentenceMap.entrySet());
        Collections.sort(infoIds,new Comparator<Map.Entry<PTM_Sentence,Float>>() {
            @Override
            public int compare(Map.Entry<PTM_Sentence, Float> o1, Map.Entry<PTM_Sentence, Float> o2) {
                return (int) (10000 * (o2.getValue() - o1.getValue()));
            }
/*            @Override
            public int compare(Map.Entry<Sentence, Double> o1, Map.Entry<Sentence, Double> o2) {
                return (int) (10000 * (o2.getValue() - o1.getValue()));
            }*/
        });
        Map<PTM_Sentence,Float> sortedSentenceMap = new HashMap<PTM_Sentence, Float>();
        for (int i = 0;i < infoIds.size();i++){
            //System.out.println(infoIds.get(i).getKey() + "\t" + infoIds.get(i).getValue());
            sortedSentenceMap.put(infoIds.get(i).getKey(),infoIds.get(i).getValue());
        }
        return sortedSentenceMap;
    }
}