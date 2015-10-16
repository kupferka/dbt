module.exports = function(grunt) {
	// Project configuration.
	grunt.initConfig({
		pkg : grunt.file.readJSON('package.json'),
		buildNumber : parseInt(grunt.option('buildNumber')) || 0,
		banner : '/*!\n' + ' * <%= pkg.name %> v${project.version}\n' + ' * Homepage: <%= pkg.homepage %>\n'
				+ ' * (c) 2013-<%= grunt.template.today("yyyy") %> <%= pkg.author %> and others. All rights reserved.\n'
				+ ' * Licensed under <%= pkg.license %>\n' + ' */\n',
		clean : {
			build : {
				src : [ "build", "dist" ]
			},
		},
		typescript : {
			base : {
				src : [ '<%= pkg.src %>/typescript/**/*.ts' ],
				dest : 'build/js/<%= pkg.name %>.js',
				options : {
					module : 'commonjs', // or amd, commonjs
					target : 'es3', // or es3
					rootDir : 'src',
					sourceMap : true,
					comments : true,
					ignoreError : false,
					declaration : false
				}
			}
		},
		concat : {
			debug : {
				files : {
					'build/<%= pkg.name %>/chrome/content/xul/<%= pkg.name %>.js' : [ 'build/js/<%= pkg.name %>.js' ]
				},
			}
		},
		uglify : {
			build : {
				options : {
					banner : '<%= banner %>',
					preserveComments : 'some',
					sourceMap : false,
					screwIE8 : true
				},
				files : {
					'build/<%= pkg.name %>/chrome/content/xul/<%= pkg.name %>.js' : [ 'build/js/<%= pkg.name %>.js' ]
				}
			}
		},
		less : {
			build : {
				options : {
					banner : '<%= banner %>',
					cleancss : true,
					compress : true,
					paths : [ "less" ]
				},
				files : {
					"build/<%= pkg.name %>/chrome/content/css/<%= pkg.name %>.css" : [ '<%= pkg.src %>/less/build.less' ]
				}
			}
		},
		copy : {
			debug : {
				expand : true,
				cwd : 'build/',
				src : '*.map',
				dest : 'build/<%= pkg.name %>/chrome/content/'
			},
			build : {
				expand : true,
				cwd : '<%= pkg.src %>/resources/',
				src : '**',
				dest : 'build/<%= pkg.name %>'
			},
		},
		compress : {
			dist : {
				options : {
					archive : 'dist/<%= pkg.name %>.zip'
				},
				files : [ {
					expand : true,
					cwd : 'build/<%= pkg.name %>/chrome',
					src : [ 'content/**/*', 'locale/**/*' ],
					dest : 'chrome/ibw'
				} ]
			}
		},
		watch : {
			scripts : {
				files : [ '<%= pkg.src %>/resources/**/*.xul', '<%= pkg.src %>/typescript/**/*.ts', '<%= pkg.src %>/less/**/*.less' ],
				tasks : [ 'typescript', 'concat', 'less', 'copy:debug', 'copy:build', 'replace:version' ],
				options : {
					spawn : false,
				},
			},
		},
		replace : {
			version : {
				options : {
					patterns : [ {
						match : 'BUILDNUMBER',
						replacement : '<%= buildNumber %>'
					} ]
				},
				files : [ {
					expand : true,
					flatten : true,
					src : [ '<%= pkg.src %>/resources/application.ini' ],
					dest : 'build/<%= pkg.name %>'
				} ]
			},
			dist : {
				options : {
					patterns : [ {
						match : 'VERSION',
						replacement : '<%= pkg.version %>'
					}, {
						match : 'REVISION',
						replacement : '<%= buildNumber %>'
					}, {
						match : /chrome:\/\/IBWRCClient\//g,
						replacement : 'chrome://ibw/'
					} ]
				},
				files : [ {
					expand : true,
					flatten : false,
					cwd : 'build/<%= pkg.name %>',
					src : [ '**/*.js', '**/*.xul' ],
					dest : 'build/<%= pkg.name %>'
				} ]
			}
		}
	});

	// Build Tasks
	grunt.loadNpmTasks('grunt-contrib-clean');
	grunt.loadNpmTasks('grunt-contrib-compress');
	grunt.loadNpmTasks('grunt-contrib-concat');
	grunt.loadNpmTasks('grunt-contrib-copy');
	grunt.loadNpmTasks('grunt-contrib-uglify');
	grunt.loadNpmTasks('grunt-contrib-less');
	grunt.loadNpmTasks('grunt-contrib-watch');
	grunt.loadNpmTasks('grunt-replace');
	grunt.loadNpmTasks('grunt-typescript');

	// Build task(s).
	grunt.registerTask('build', [ 'typescript', 'uglify', 'less', 'copy:build', 'replace:version' ]);

	// Default task
	grunt.registerTask('default', [ 'clean', 'build', 'replace:dist', 'compress' ]);
};