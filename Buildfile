#
#  Copyright (C) 2009, Intalio Inc.
#
#  The program(s) herein may be used and/or copied only with the
#  written permission of Intalio Inc. or in accordance with the terms
#  and conditions stipulated in the agreement/contract under which the
#  program(s) have been supplied.

require "buildr4osgi"

require File.join(File.dirname(__FILE__), "repositories.rb")
require File.join(File.dirname(__FILE__), "dependencies.rb")

VERSION_NUMBER = "0.0.1.001-SNAPSHOT"

desc "OSGI webapp: bootstrap jetty inside osgi and let other bundle register self-contained webapps"
define "org.intalio.osgi" do
  project.version = VERSION_NUMBER
  project.group = "org.intalio.osgi"  
  desc "Bootstrap Jetty Server and let other bundles register self-contained webapplications"
  define "org.intalio.osgi.jetty.server" do
    project.version = VERSION_NUMBER
    p project.version
    compile.with project.dependencies
    package(:plugin)

  end

  desc "Example of self container webapp"
  define "org.intalio.osgi.examplewebapp" do
    project.version = VERSION_NUMBER
    project.group = "org.intalio.osgi"
    p project.version
    compile.with [project('org.intalio.osgi.jetty.server')] + project.dependencies
    package(:plugin)
  end
end