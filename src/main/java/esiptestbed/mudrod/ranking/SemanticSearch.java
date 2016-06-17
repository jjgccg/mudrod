package esiptestbed.mudrod.ranking;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolFilterBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.sort.SortOrder;

import esiptestbed.mudrod.discoveryengine.MudrodAbstract;
import esiptestbed.mudrod.driver.ESDriver;
import esiptestbed.mudrod.driver.SparkDriver;

public class SemanticSearch extends MudrodAbstract {

	public SemanticSearch(Map<String, String> config, ESDriver es, SparkDriver spark) {
		super(config, es, spark);
		// TODO Auto-generated constructor stub
		ClickstreamImporter cs = new ClickstreamImporter(config, es, spark);
		cs.importfromCSVtoES();
	}

	//List<Result> resultList = new ArrayList<Result>();

	class Result
	{
		String shortName = null;
		String longName = null;
		String topic = null;
		String description = null;
		String date = null;
		String factor = null;
		float order = -9999;

		public Result(String shortName, float order, String longName, String topic, String description, String date, String factor){
			this.shortName = shortName;
			this.order = order;
			this.longName = longName;
			this.topic = topic;
			this.description = description;
			this.date = date;
			this.factor = factor;

		}

	}
	
	public class ResultComparator implements Comparator<Result> {
	    @Override
	    public int compare(Result o1, Result o2) {
			return Double.compare(o2.order, o1.order);
		}
	}

	public BoolQueryBuilder createSemQuery(){
		Map<String, String> terms_map = new HashMap<String, String>();
		terms_map.put("ocean wind", "1");
		terms_map.put("surface wind", "0.9");

		String fieldsList[] = {"Dataset-Metadata", "Dataset-ShortName", "Dataset-LongName", "Dataset-Description", "DatasetParameter-*"};

		BoolQueryBuilder qb = new BoolQueryBuilder();
		for (Map.Entry<String, String> entry : terms_map.entrySet()){
			qb.should(QueryBuilders.multiMatchQuery(entry.getKey(), fieldsList)
					.boost(Float.parseFloat(entry.getValue()))
					.type(MultiMatchQueryBuilder.Type.PHRASE));
		}

		return qb;
	}

	public List<Result> semQuery(String query){
		List<Result> resultList = new ArrayList<Result>();
		String index = config.get("indexName");
		String type = config.get("raw_metadataType");
		boolean exists = es.node.client().admin().indices().prepareExists(index).execute().actionGet().isExists();	
		if(!exists){
			return null;
		}

		BoolQueryBuilder qb = createSemQuery();
		SearchResponse response = es.client.prepareSearch(index)
				.setTypes(type)		        
				.setQuery(qb)
				.setSize(500)
				.execute()
				.actionGet();

		int i = 1;
		System.out.println(response.getHits().getHits().length);
		for (SearchHit hit : response.getHits().getHits()) {
			Map<String,Object> result = hit.getSource();
			String shortName = (String) result.get("Dataset-ShortName");
			String longName = (String) result.get("Dataset-LongName");
			@SuppressWarnings("unchecked")
			ArrayList<String> topicList = (ArrayList<String>) result.get("DatasetParameter-Variable");
			String topic = String.join(", ", topicList);
			String content = (String) result.get("Dataset-Description");
			@SuppressWarnings("unchecked")
			ArrayList<String> longdate = (ArrayList<String>) result.get("DatasetCitation-ReleaseDateLong");

			Date date=new Date(Long.valueOf(longdate.get(0)).longValue());
			SimpleDateFormat df2 = new SimpleDateFormat("dd/MM/yy");
			String dateText = df2.format(date);

			Result re = new Result(shortName, (float)i, longName, topic, content, dateText, "term");
			resultList.add(re); 
			i++;
		}

		return resultList;

	}

	public List<Result> addRecency(String query){
		List<Result> resultList = new ArrayList<Result>();

		String index = config.get("indexName");
		String type = config.get("raw_metadataType");

		BoolQueryBuilder qb = createSemQuery();
		SearchResponse response = es.client.prepareSearch(index)
				.setTypes(type)		        
				.setQuery(qb)
				.setSize(500)
				.addSort("DatasetCitation-ReleaseDateLong", SortOrder.DESC)
				.execute()
				.actionGet();

		int i = 1;

		for (SearchHit hit : response.getHits().getHits()) {
			Map<String,Object> result = hit.getSource();
			String shortName = (String) result.get("Dataset-ShortName");

			Result re = new Result(shortName, (float)i, null, null, null, null, "recency");
			resultList.add(re);
			i++;
		}

		return resultList;
	}

	public List<Result> addUserclicks(String query, Map<String, String> terms_map){
		List<Result> resultList = new ArrayList<Result>();

		terms_map = new HashMap<String, String>();
		terms_map.put("ocean wind", "1");
		terms_map.put("surface wind", "0.5");

		String index = config.get("indexName");
		String type = config.get("raw_metadataType");

		BoolQueryBuilder qb = createSemQuery();
		SearchResponse response = es.client.prepareSearch(index)
				.setTypes(type)		        
				.setQuery(qb)
				.setSize(500)
				.execute()
				.actionGet();

		for (SearchHit hit : response.getHits().getHits()) {
			Map<String,Object> result = hit.getSource();
			String shortName = (String) result.get("Dataset-ShortName");	
			float click_count =0;

			BoolFilterBuilder bf = new BoolFilterBuilder();
			bf.must(FilterBuilders.termFilter("dataID", shortName));

			for (Map.Entry<String, String> entry : terms_map.entrySet()){
				bf.should(FilterBuilders.termFilter("query", entry.getKey()));			
			}
			QueryBuilder click_search = QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(), bf);
			SearchResponse clicks_res = es.client.prepareSearch(index)
					.setTypes(config.get("clickstreamMatrixType"))		        
					.setQuery(click_search)
					.setSize(500)
					.execute()
					.actionGet();

			for (SearchHit item : clicks_res.getHits().getHits()) {
				Map<String,Object> click = item.getSource();
				float click_frequency = Float.parseFloat((String) click.get("clicks"));
				String query_str = (String) click.get("query");
				float query_weight = Float.parseFloat(terms_map.get(query_str));
				click_count += click_frequency * query_weight;
			}

			Result re = new Result(shortName, click_count, null, null, null, null, "clicks");
			resultList.add(re);
		}	

		Collections.sort(resultList, new ResultComparator());

		int i = 0;
		float last = 0;
		float tmp = 0;
		for(int j =0; j<resultList.size(); j++)
		{	
			tmp = last;
			last = resultList.get(j).order;
			/*if(j == 0 && resultList.get(j).order!=0.0)
			{
				i++;
			}else{*/
			if(tmp!= resultList.get(j).order)
			{
				i++;
			}	
			//}
			resultList.get(j).order = i;
			System.out.println(resultList.get(j).order);
		}
		return resultList;
	}

	public void combine(List<Result> list0, List<Result> list1, List<Result> list2)
	{
		List<Result> resultList = new ArrayList<Result>();
		List<Result> combined = new ArrayList<Result>();
		combined.addAll(list0);
		combined.addAll(list1);
		combined.addAll(list2);

		Map<String, List<Result>> map = combined.stream().collect(Collectors.groupingBy(w -> w.shortName));		
		for (Entry<String, List<Result>> entry : map.entrySet()) {		
			List<Result> list = entry.getValue();
			float sum = 0;
			Result re = null;
			for (Result ele : list) {
				if(ele.factor.equals("term"))
				{
					re = ele;
				}
				sum += getFactorW(ele.factor)/(ele.order+2);
				System.out.println(ele.factor + ": " + ele.order);
			}
			re.order = sum;
			re.factor = "combined";
			resultList.add(re);		
		}		
		Collections.sort(resultList, new ResultComparator());
	}

	public double getFactorW(String factor)
	{
		if(factor.equals("term"))
		{
			return 1;
		}else if(factor.equals("recency"))
		{
			return 1;
		}else if(factor.equals("clicks")){
			return 1;
		}

		return -9999;
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
