properties([
	buildDiscarder(logRotator(numToKeepStr: '10')),
	disableConcurrentBuilds(),
	pipelineTriggers([
		cron('H * * * *'),
	]),
])

// we can't use "load()" here because we don't have a file context (or a real checkout of "oi-janky-groovy" -- the pipeline plugin hides that checkout from the actual pipeline execution)
def vars = fileLoader.fromGit(
	'multiarch/vars.groovy', // script
	'https://github.com/docker-library/oi-janky-groovy.git', // repo
	'master', // branch
	null, // credentialsId
	'master', // node/label
)

node {
	env.BASHBREW_LIBRARY = env.WORKSPACE + '/oi/library'

	archNamespaces = []
	for (arch in (vars.arches + 'amd64')) {
		archNamespaces << arch + ' = ' + vars.archNamespace(arch)
	}
	env.BASHBREW_ARCH_NAMESPACES = archNamespaces.join(', ')

	stage('Checkout') {
		checkout(
			poll: true,
			scm: [
				$class: 'GitSCM',
				userRemoteConfigs: [[
					url: 'https://github.com/docker-library/official-images.git',
				]],
				branches: [[name: '*/master']],
				extensions: [
					[
						$class: 'CleanCheckout',
					],
					[
						$class: 'RelativeTargetDirectory',
						relativeTargetDir: 'oi',
					],
					[
						$class: 'PathRestriction',
						excludedRegions: '',
						includedRegions: 'library/**',
					],
				],
				doGenerateSubmoduleConfigurations: false,
				submoduleCfg: [],
			],
		)
	}

	stage('Put Shared') {
		sh '''
			bashbrew list --all --repos \\
				| xargs -P "$(( $(nproc) * 2 ))" -n1 \\
					bashbrew put-shared --namespace trollin
		'''
	}
}