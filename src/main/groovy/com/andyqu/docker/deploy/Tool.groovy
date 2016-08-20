package com.andyqu.docker.deploy

import de.gesellix.docker.client.DockerClientImpl
import de.gesellix.docker.client.DockerResponse
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.security.MessageDigest

import com.andyqu.docker.deploy.history.DeployHistory

class Tool {
	def static final LOGGER = LoggerFactory.getLogger("Tool")
	JsonSlurper slurper = new JsonSlurper()
    def  static generateMD5(String s) {
        MessageDigest digest = MessageDigest.getInstance("MD5")
        digest.update(s.bytes);
        new BigInteger(1, digest.digest()).toString(16).padLeft(32, '0')
    }

    def  static boolean isPortInUse(String hostName, int portNumber) {
        boolean result;
        try {
            Socket s = new Socket(hostName, portNumber);
            s.close();
            true
        } catch (Exception e) {
            false
        }
    }
	def Tool(){
		extendSlf4j()
		extendBufferedReader()
		extendObject()
		LOGGER.info "event_name=Tool_bean_created"
	}
    def   extendSlf4j(){
        Logger.metaClass.trace={
            msgObj->
                trace(new JsonBuilder(msgObj).toPrettyString())
        }
        Logger.metaClass.debug={
            msgObj->
                debug(new JsonBuilder(msgObj).toPrettyString())
        }
        Logger.metaClass.info={
            msgObj->
                info(new JsonBuilder(msgObj).toPrettyString())
        }
        Logger.metaClass.warn={
            msgObj->
                warn(new JsonBuilder(msgObj).toPrettyString())
        }
        Logger.metaClass.error={
            msgObj->
                error(new JsonBuilder(msgObj).toPrettyString())
        }
		this
    }

    def   extendBufferedReader(){
        BufferedReader.metaClass.readLine={
            str->
                print str
                readLine()
        }
		this
    }
	
	def   extendObject(){
		Object.metaClass.toMap = {
				return slurper.parseText(new JsonBuilder(delegate).toString())
		}
		Object.metaClass.toString={
			LOGGER.info "event_name=injected_toString_called"
			return new JsonBuilder(delegate).toPrettyString()
		}
		this
	}
}



class DockerTool {
	def static final LOGGER = LoggerFactory.getLogger("DockerTool")
	def DockerTool(){
		extendDockerClientImpl()
		LOGGER.info "event_name=DockerTool_bean_created"
	}
    def   void extendDockerClientImpl(){
        /*
        DockerClientImpl在gesellix/docker-client的15年版本中，日志器名字是logger。
        在16年版本中，日志器名字是log。
        为了保证部署系统的独立性，在这里加一个我们自己的日志器loggeR
        */
        addLogger()
        addQueryContainerName()
        addStopAndRemoveContainer()
		addQueryAllContainerPorts()
    }
    def   void addLogger(){
        DockerClientImpl.metaClass.loggeR=LoggerFactory.getLogger(DockerTool)
    }
    def   void addQueryContainerName() {
        DockerClientImpl.metaClass.queryContainerName = {
            containerName ->
                DockerResponse response = ps(query: [all: true, size: true])
                def foundContainer = null
                if (response.status.success) {
                    response.content.find {
                        container ->
                            container.Names.find {
                                name ->
                                    if (name.contains(containerName)) {
                                        foundContainer = container
                                        true
                                    } else {
                                        false
                                    }
                            }
                    }
                }
                foundContainer
        }
    }

    def   void addStopAndRemoveContainer() {
        DockerClientImpl.metaClass.stopAndRemoveContainer = {
            containerId ->
                DockerResponse response = stop(containerId)
                if (response.status.success || response.status.code == 304) {
                    response = rm(containerId)
                    if (response.status.success) {
                        loggeR.info("remove ${containerId} successfully")
                    }else{
                        loggeR.error("remove ${containerId} failed")
                        loggeR.error(response)
                    }
                }else{
                    loggeR.error("stop ${containerId} failed")
                    loggeR.error(response)
                }
        }
    }
	
	def   void addQueryAllContainerPorts() {
		DockerClientImpl.metaClass.queryAllContainerPorts = {
			DockerResponse response = ps(query: [all: true, size: true])
			if (response.status.success) {
				response.content.collect {
					container->
						container.Ports.collect {
							portConf->
								portConf.PublicPort
						}
				}.flatten()
			}else{
				loggeR.error "event_name=ps-all-containers-fail response=${response}"
				[]
			}
		}
	}
}
