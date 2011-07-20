/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
 Part of the Processing project - http://processing.org

 Copyright (c) 2004-11 Ben Fry and Casey Reas
 Copyright (c) 2001-04 Massachusetts Institute of Technology

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package processing.app;

import java.io.*;
import java.net.*;
import java.util.*;

import javax.xml.parsers.*;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

import processing.app.Contribution.ContributionInfo;
import processing.app.Contribution.ContributionInfo.Author;
import processing.app.Contribution.ContributionInfo.ContributionType;
import processing.app.Library.LibraryInfo;
import processing.app.LibraryCompilation.LibraryCompilationInfo;

public class ContributionListing {
  
  ArrayList<ContributionChangeListener> listeners;
  
  ArrayList<ContributionInfo> advertisedContributions;
  
  Map<String, List<ContributionInfo>> librariesByCategory;
  
  ArrayList<ContributionInfo> allLibraries;
  
  
  public ContributionListing() {
    listeners = new ArrayList<ContributionChangeListener>();
    librariesByCategory = new HashMap<String, List<ContributionInfo>>();
    allLibraries = new ArrayList<ContributionInfo>();
  }


  public void setAdvertisedList(File xmlFile) {
    
    ContributionXmlParser xmlParser = new ContributionXmlParser(xmlFile);
    advertisedContributions = xmlParser.getLibraries();
    for (ContributionInfo info : advertisedContributions) {
      addContribution(info);
    }
    
    Collections.sort(allLibraries);
    
  }
  
  /**
   * If a the given contribution has a version of it which is advertised. This
   * method sets the latestVersion and category for the contribution.
   */
  public void getInformationFromAdvertised(ContributionInfo contribution) {
    
    ContributionInfo advertised = getAdvertisedContribution(contribution.name,
                                                            contribution.getType());
    contribution.setAdvertisedVersion(advertised);

    if (advertised != null) {
      // Merge information from the advertised and local versions.
      if (advertised.category != null) {
        contribution.category = advertised.category;
      }
    }
  }
  
  /**
   * Adds the installed libraries to the listing of libraries, replacing any
   * pre-existing libraries by the same name as one in the list.
   */
  public void updateInstalledList(List<ContributionInfo> installedContributions) {
    
    for (ContributionInfo contribution : installedContributions) {
      
      getInformationFromAdvertised(contribution);
      
      boolean found= false;
      for (ContributionInfo existing : allLibraries) {
        if (existing.name.equals(contribution.name) && existing.getType() == contribution.getType()) {
          replaceContribution(existing, contribution);
          found = true;
          break;
        }
      }
      
      if (!found) {
        addContribution(contribution);
      }
    }
    
  }
  
  
  public void replaceContribution(ContributionInfo oldLib, ContributionInfo newLib) {
    
    if (oldLib == null || newLib == null) {
      return;
    }
    
    if (librariesByCategory.containsKey(oldLib.category)) {
      List<ContributionInfo> list = librariesByCategory.get(oldLib.category);
      
      for (int i = 0; i < list.size(); i++) {
        if (list.get(i) == oldLib) {
          list.set(i, newLib);
        }
      }
    }
    
    for (int i = 0; i < allLibraries.size(); i++) {
      if (allLibraries.get(i) == oldLib) {
        allLibraries.set(i, newLib);
      }
    }
    
    notifyChange(oldLib, newLib);
  }
  
  public void addContribution(ContributionInfo libInfo) {
    
    if (librariesByCategory.containsKey(libInfo.category)) {
      List<ContributionInfo> list = librariesByCategory.get(libInfo.category);
      list.add(libInfo);
      
      Collections.sort(list);
    } else {
      ArrayList<ContributionInfo> libs = new ArrayList<ContributionInfo>();
      libs.add(libInfo);
      librariesByCategory.put(libInfo.category, libs);
    }
    allLibraries.add(libInfo);
    
    notifyAdd(libInfo);
    
    Collections.sort(allLibraries);
  }
  
  public void removeContribution(ContributionInfo info) {
    if (librariesByCategory.containsKey(info.category)) {
      librariesByCategory.get(info.category).remove(info);
    }
    allLibraries.remove(info);
    
    notifyRemove(info);
  }
  
  public ContributionInfo getAdvertisedContribution(String contributionName,
                                                    ContributionType contributionType) {
    
    for (ContributionInfo advertised : advertisedContributions) {
      
      if (advertised.getType() == contributionType
          && advertised.name.equals(contributionName)) {
        
        return advertised;
      }
      
    }
    
    return null;
  }
  
  public Set<String> getCategories() {
    return librariesByCategory.keySet();
  }

  public List<ContributionInfo> getAllLibararies() {
    return new ArrayList<ContributionInfo>(allLibraries);
  }

  public List<ContributionInfo> getLibararies(String category) {
    ArrayList<ContributionInfo> libinfos = new ArrayList<ContributionInfo>(librariesByCategory.get(category));
    Collections.sort(libinfos);
    return libinfos;
  }
  
  public List<ContributionInfo> getFilteredLibraryList(String category, List<String> filters) {
    ArrayList<ContributionInfo> filteredList = new ArrayList<ContributionInfo>(allLibraries);
    
    Iterator<ContributionInfo> it = filteredList.iterator();
    while (it.hasNext()) {
      ContributionInfo libInfo = it.next();
      
      if (category != null && !category.equals(libInfo.category)) {
        it.remove();
      } else {
        for (String filter : filters) {
          if (!matches(libInfo, filter)) {
            it.remove();
            break;
          }
        }
      }
      
    }
    
    return filteredList;
  }

  private boolean matches(ContributionInfo info, String filter) {
    
    // Maybe this can be fancy some other time
    if (filter.equals("has:update") || filter.equals("has:updates")) {
      return info.hasUpdates();
    }
    if (filter.equals("is:installed")) {
      return info.isInstalled();
    }
    if (filter.equals("not:installed")) {
      return !info.isInstalled();
    }
    if (filter.contains(":")) {
      // Return true and ignore everything else if the property is invalid.
      return true;
    }
    // sdfikjfdslk Added filters properties for showing installed and upgrade 
    filter = ".*" + filter.toLowerCase() + ".*";
    
    if (filter.isEmpty()) {
      return true;
    }
    
    for (Author author : info.authorList) {
      if (author.name.toLowerCase().matches(filter)) {
        return true;
      }
    }
    
    return info.sentence != null && info.sentence.toLowerCase().matches(filter)
        || info.paragraph != null && info.paragraph.toLowerCase().matches(filter)
        || info.category != null && info.category.toLowerCase().matches(filter)
        || info.name != null && info.name.toLowerCase().matches(filter);
 
  }

  private void notifyRemove(ContributionInfo ContributionInfo) {
    for (ContributionChangeListener listener : listeners) {
      listener.contributionRemoved(ContributionInfo);
    }
  }
  
  private void notifyAdd(ContributionInfo ContributionInfo) {
    for (ContributionChangeListener listener : listeners) {
      listener.contributionAdded(ContributionInfo);
    }
  }
  
  private void notifyChange(ContributionInfo oldLib, ContributionInfo newLib) {
    for (ContributionChangeListener listener : listeners) {
      listener.contributionChanged(oldLib, newLib);
    }
  }
  
  public void addContributionListener(ContributionChangeListener listener) {
    listeners.add(listener);
  }
  
  public void removeContributionListener(ContributionChangeListener listener) {
    listeners.remove(listener);
  }
  
  public ArrayList<ContributionChangeListener> getContributionListeners() {
    return new ArrayList<ContributionChangeListener>(listeners);
  }
  
  public void getAdvertisedContributions(ProgressMonitor pm) {
  
    final ContributionListFetcher llf = new ContributionListFetcher();
    llf.setProgressMonitor(pm);
    new Thread(llf).start();
  }
  
  public static interface ContributionChangeListener {
    
    public void contributionAdded(ContributionInfo ContributionInfo);
    
    public void contributionRemoved(ContributionInfo ContributionInfo);
    
    public void contributionChanged(ContributionInfo oldLib, ContributionInfo newLib);
    
  }
  
  public class ContributionListFetcher implements Runnable {

    File dest;
    
    URL url;
    
    FileDownloader downloader;
    
    Thread downloaderThread;
    
    ProgressMonitor progressMonitor;

    public ContributionListFetcher() {
      
      progressMonitor = new NullProgressMonitor();
      
      try {
        File tmpFolder = Base.createTempFolder("libarylist", "download");

        dest = new File(tmpFolder, "contributions.xml");
        dest.setWritable(true);

        url = new URL("http://dl.dropbox.com/u/700641/generated/contributions.xml");

      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    
    public void setProgressMonitor(ProgressMonitor pm) {
      progressMonitor = pm;
    }

    public void run() {
      downloader = new FileDownloader(url, dest, progressMonitor);
      downloader.setPostOperation(new Runnable() {
        
        public void run() {
          
          File xmlFile = downloader.getFile();
          if (xmlFile != null) {
            setAdvertisedList(xmlFile);
          }
        }
      });
      
      downloader.run();
    }
    
  }

  /**
   * Class to parse the libraries xml file
   */
  private static class ContributionXmlParser extends DefaultHandler {
    
    ArrayList<ContributionInfo> libraries;
    
    String currentCategoryName;

    ContributionInfo currentInfo;

    ContributionXmlParser(File xmlFile) {
      SAXParserFactory spf = SAXParserFactory.newInstance();
      spf.setValidating(false);
      
      try {
        SAXParser sp = spf.newSAXParser(); // throws ParserConfigurationException

        InputSource input = new InputSource(new FileReader(xmlFile));

        libraries = new ArrayList<ContributionInfo>();
        sp.parse(input, this); // throws SAXException

      } catch (ParserConfigurationException e) {
        Base.showWarning("Error reading contributions list",
                         "An internal error occured when preparing to read the list\n" +
                             "of libraries. You can still install libraries manually while\n" +
                             "we work on fixing this.", e);
      } catch (IOException e) {
        Base.showWarning("Error reading contributions list",
                         "A error occured while reading the list of available libraries.\n" +
                         "Try restarting the Contribution Manager.\n", e);
      } catch (SAXException e) {
        Base.showWarning("Error reading contributions list",
                         "The list of libraries downloaded from Processing.org\n" +
                         "appears to be malformed. You can still install libraries\n" + 
                         "manually while we work on fixing this.", e);
        libraries = null;
      }
    }

    public ArrayList<ContributionInfo> getLibraries() {
      return libraries;
    }

    @Override
    public void startElement(String uri, String localName, String qName,
                             Attributes attributes) throws SAXException {

      if ("category".equals(qName)) {
        currentCategoryName = attributes.getValue("name");

      } else if ("library".equals(qName)) {
        currentInfo = new LibraryInfo();
        setCommonAttributes(attributes);

      } else if ("librarycompilation".equals(qName)) {
        LibraryCompilationInfo compilationInfo = new LibraryCompilationInfo();
        String[] names = attributes.getValue("libraryNames").split(";");
        for (int i = 0; i < names.length; i++) {
          names[i] = names[i].trim();
        }
        compilationInfo.libraryNames = Arrays.asList(names);
        currentInfo = compilationInfo;
        setCommonAttributes(attributes);
        
      } else if ("author".equals(qName)) {
        Author author = new Author();
        author.name = attributes.getValue("name");
        author.url = attributes.getValue("url");
        currentInfo.authorList.add(author);

      } else if ("description".equals(qName)) {
        currentInfo.sentence = attributes.getValue("sentence");
        currentInfo.paragraph = attributes.getValue("paragraph");
        
      } else if ("version".equals(qName)) {
        currentInfo.version = Integer.parseInt(attributes.getValue("id"));
        currentInfo.prettyVersion = attributes.getValue("pretty");

      } else if ("location".equals(qName)) {
        currentInfo.link = attributes.getValue("url");

      }
      
    }
    
    private void setCommonAttributes(Attributes attributes) {
      currentInfo.authorList = new ArrayList<Author>();
      currentInfo.category = currentCategoryName;
      currentInfo.name = attributes.getValue("name");
      currentInfo.url = attributes.getValue("url");
    }
    
    @Override
    public void endElement(String uri, String localName, String qName)
        throws SAXException {

      if ("library".equals(qName) || "librarycompilation".equals(qName)) {
        libraries.add(currentInfo);
        currentInfo = null;
      }
    }

    @Override
    public void warning(SAXParseException exception) {
      System.err.println("WARNING: line " + exception.getLineNumber() + ": "
          + exception.getMessage());
    }

    @Override
    public void error(SAXParseException exception) {
      System.err.println("ERROR: line " + exception.getLineNumber() + ": "
          + exception.getMessage());
    }

    @Override
    public void fatalError(SAXParseException exception) throws SAXException {
      System.err.println("FATAL: line " + exception.getLineNumber() + ": "
          + exception.getMessage());
      throw (exception);
    }
  }

  public boolean hasUpdates() {
    for (ContributionInfo info : allLibraries) {
      if (info.hasUpdates()) {
        return true;
      }
    }
    return false;
  }

}