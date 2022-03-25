import groovy.text.GStringTemplateEngine

def getLibraryResource(String resource){
    def contents = libraryResource resource

    def engine = new GStringTemplateEngine()
    def template = engine.createTemplate(contents).make([okapiUrl: "bla"])
    return template.toString()
}
