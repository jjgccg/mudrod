/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you 
 * may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package esiptestbed.mudrod.ontology.pre;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.filter.ElementFilter;
import org.jdom2.input.SAXBuilder;

import esiptestbed.mudrod.discoveryengine.DiscoveryStepAbstract;
import esiptestbed.mudrod.driver.ESDriver;
import esiptestbed.mudrod.driver.SparkDriver;

public class AggregateTriples extends DiscoveryStepAbstract {

	public AggregateTriples(Map<String, String> config, ESDriver es, SparkDriver spark) {
		super(config, es, spark);
		// TODO Auto-generated constructor stub
	}

	@Override
	public Object execute() {
		// TODO Auto-generated method stub
		/*File file = null;
		try {
			file = File.createTempFile("oceanTriples", ".csv");
			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			bw = new BufferedWriter(fw);
			
		} catch (IOException e3) {
			// TODO Auto-generated catch block
			e3.printStackTrace();
		}*/
		
        File file = new File(this.config.get("oceanTriples"));
		if (file.exists()) {
			file.delete();			
		}
		try {
			file.createNewFile();
		} catch (IOException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}

		FileWriter fw;
		try {
			fw = new FileWriter(file.getAbsoluteFile());
			bw = new BufferedWriter(fw);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		File[] files = new File(this.config.get("ontologyInputDir")).listFiles();
		for (File file_in : files) {      
		String ext = FilenameUtils.getExtension(file_in.getAbsolutePath());
		if(ext.equals("owl")){
			try {
				loadxml(file_in.getAbsolutePath());
				getAllClass();
			} catch (JDOMException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

		}
	    }
	    }*/
		
		/*try {
			List<String> files = IOUtils.readLines(MudrodEngine.class.getClassLoader()
			        .getResourceAsStream("SWEET_ocean/"));
			
			for (String f : files) {      
				InputStream owlStream = MudrodEngine.class.getClassLoader().getResourceAsStream("SWEET_ocean/" + f);
				try {
					loadxml(owlStream);
				} catch (JDOMException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				getAllClass();
			}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}*/

		try {
			bw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return file;
	}

	public Document document;
	public Element rootNode=null;
	final static String owl_namespace = "http://www.w3.org/2002/07/owl#";
	final static String rdf_namespace = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
	final static String rdfs_namespace = "http://www.w3.org/2000/01/rdf-schema#";

	BufferedWriter bw = null;

	public void loadxml(String filePathName) throws JDOMException, IOException{
		SAXBuilder saxBuilder = new SAXBuilder();
		File file=new File(filePathName);

		document = saxBuilder.build(file);
		rootNode = document.getRootElement();

	}
	
	/*public void loadxml(InputStream owlStream) throws JDOMException, IOException{
		SAXBuilder saxBuilder = new SAXBuilder();
		document = saxBuilder.build(owlStream);
		rootNode = document.getRootElement();

	}*/

	public void loopxml(){
		Iterator<?> processDescendants = rootNode.getDescendants(new ElementFilter()); 
		String text="";

		while(processDescendants.hasNext()) {
			Element e =  (Element) processDescendants.next();
			String currentName = e.getName();
			text=e.getTextTrim();
			if(text.equals("")){
				System.out.println(currentName);
			}else{
				System.out.println(currentName+":"+text);
			}
		}
	}

	public Element findChild(String str, Element ele){
		Iterator<?> processDescendants = ele.getDescendants(new ElementFilter()); 
		String name="";
		Element result=null;

		while(processDescendants.hasNext()) {
			Element e =  (Element) processDescendants.next();
			name=e.getName();
			if(name.equals(str)){
				result=e;
				return result;
			}
		}
		return result;

	}

	public void getAllClass() throws IOException{
		List classElements = rootNode.getChildren("Class",Namespace.getNamespace("owl",owl_namespace));

		for(int i = 0 ; i < classElements.size() ; i++) {
			Element classElement = (Element) classElements.get(i);
			String className = classElement.getAttributeValue("about", Namespace.getNamespace("rdf",rdf_namespace));

			if(className == null){
				className = classElement.getAttributeValue("ID", Namespace.getNamespace("rdf",rdf_namespace));
			}

			List SubclassElements = classElement.getChildren("subClassOf",Namespace.getNamespace("rdfs",rdfs_namespace));
			for(int j = 0 ; j < SubclassElements.size() ; j++)
			{
				Element subclassElement = (Element) SubclassElements.get(j);
				String SubclassName = subclassElement.getAttributeValue("resource", Namespace.getNamespace("rdf",rdf_namespace));
				if(SubclassName==null)
				{
					Element allValuesFromEle = findChild("allValuesFrom", subclassElement);
					if(allValuesFromEle!=null){
						SubclassName = allValuesFromEle.getAttributeValue("resource", Namespace.getNamespace("rdf",rdf_namespace));
						bw.write(cutString(className) + ",SubClassOf," + cutString(SubclassName) + "\n");	
					}
				}
				else
				{
					bw.write(cutString(className) + ",SubClassOf," + cutString(SubclassName) + "\n");
				}

			}

			List EqualclassElements = classElement.getChildren("equivalentClass",Namespace.getNamespace("owl",owl_namespace));
			for(int k = 0 ; k < EqualclassElements.size() ; k++)
			{
				Element EqualclassElement = (Element) EqualclassElements.get(k);
				String EqualclassElementName = EqualclassElement.getAttributeValue("resource", Namespace.getNamespace("rdf",rdf_namespace));

				if(EqualclassElementName!=null)
				{
					bw.write(cutString(className) + ",equivalentClass," + cutString(EqualclassElementName) + "\n");
				}
			}


		}
	}

	public String cutString(String str)
	{
		str = str.substring(str.indexOf("#")+1);
		String[] str_array = str.split("(?=[A-Z])");
		str = Arrays.toString(str_array);
		return str.substring(1, str.length()-1).replace(",", "");
	}

	@Override
	public Object execute(Object o) {
		// TODO Auto-generated method stub
		return null;
	}

}
