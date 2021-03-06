package hive.parser

import util.MD5

class HiveJobParser {
	def parse(f, verbose) {
		def queryMap = new LinkedHashMap()
			
		def sqlmd5
		def sessionStartTime = 0L
		def jobStartTime = 0L
		def taskStartTime = 0L
		
		def counterMap = [:]
		
		f.eachLine { line ->
			switch(line) {
				/**
				 * session
				 */
				case ~/^SessionStart.+/ :
					def m = line =~ /^SessionStart.*"(.+?)".*/
					sessionStartTime = Long.parseLong(m[0][1])
					break
				
				/**
				 * job
				 */
				case ~/(?m)^QueryStart\s+.+/ :
					def m = line =~ /(?m)^QueryStart\s+QUERY_STRING="(.+?)"\s+QUERY_ID="(.+?)"\s+TIME="(.+?)"/
					sqlmd5 = MD5.md5(m[0][1])
					jobStartTime = Long.parseLong(m[0][3])
					//queryMap[sqlmd5] = { 'startTime' + }
				    break
				case ~/(?m)^QueryEnd\s+.+/ :
					def m = line =~ /(?m)^QueryEnd\s+.*QUERY_STRING="(.+?)"\s+QUERY_ID="(.+?)"\s+.+TIME="(.+?)"/
					sqlmd5 = MD5.md5(m[0][1])

                    if (m[0][1].trim().startsWith("CREATE TEMPORARY FUNCTION"))
						break

					printf '         * %s | %10s + %s | %s\n', 
							new java.text.SimpleDateFormat('HH:mm:ss').format(jobStartTime),
	                        Long.parseLong(m[0][3]) - jobStartTime,
							MD5.md5(m[0][1]),
	                        m[0][1].trim().substring(0, Math.min(50, m[0][1].trim().size()))
				    break
				/**
				 * task
				 */
				case ~/(?m)^TaskStart\s+.+/ :
					def m = line =~ /(?m)^TaskStart\s+.*TASK_NAME="(.+?)"\s+.*TASK_ID="(.+?)"\s+.*TIME="(.+?)"/
					taskStartTime = Long.parseLong(m[0][3])
					break
				case ~/(?m)^TaskEnd\s+.+/ :
					
					def m = line =~ /(?m)^TaskEnd\s+.*TASK_NAME="(.+?)"\s+.*TASK_ID="(.+?)"\s+.*TIME="(.+?)"/
					def mm = line =~ /TASK_HADOOP_ID="(.+?)"/
					def mm_m = line =~ /TASK_NUM_MAPPERS="(.+?)"/
					def mm_r = line =~ /TASK_NUM_REDUCERS="(.+?)"/
					
					def hdfsBytesRead = (line =~ /FileSystemCounters\.HDFS_BYTES_READ:([0-9]+)/).size() > 0 ? (line =~ /FileSystemCounters\.HDFS_BYTES_READ:([0-9]+)/)[0][1] : ''
					def hdfsBytesWritten = (line =~ /FileSystemCounters\.HDFS_BYTES_WRITTEN:([0-9]+)/).size() > 0 ? (line =~ /FileSystemCounters\.HDFS_BYTES_WRITTEN:([0-9]+)/)[0][1] : ''
					def inputRecords = (line =~ /input records:([0-9]+)/).size() > 0 ? (line =~ /input records:([0-9]+)/)[0][1] : 0
					def outputRecords = (line =~ /output records:([0-9]+)/).size() > 0 ? (line =~ /output records:([0-9]+)/)[0][1] : 0

                    if (m[0][1].endsWith('FunctionTask'))
						break

					printf '                      %10s | %s - %s %s %s, hdfs_r=%s, hdfs_w=%s, input_rec=%s, output_rec=%s\n', 
	                        Long.parseLong(m[0][3]) - taskStartTime,
	                        m[0][2],
							m[0][1].replace('org.apache.hadoop.hive.ql.exec.', ''),
							mm.size() > 0 ? '(' + mm[0][1] + ')' : '',
							mm_m.size() > 0 && mm_r.size() > 0 ? '(' + mm_m[0][1] + '/' + mm_r[0][1] + ')' : '',
							hdfsBytesRead,
							hdfsBytesWritten,
							inputRecords,
							outputRecords

                    if (verbose < 1)
					    break

					//println '~'*100
					line.split(',').each { kv ->
					    println ' '*35 + kv
					}
					//println '~'*100

				    break
				case ~/(?m)^TaskProgress\s+.+/ :
						
					if (verbose < 1)
						break

					def m = line =~ /(?m)^TaskProgress\s+.*TASK_HADOOP_PROGRESS="(.+?)"\s+.*TASK_NAME="(.+?)"\s+.*TASK_COUNTERS="(.+?)"/
					printf '                                 . %s\n', m[0][1]

					if (verbose < 2)
						break
						
					def mm = m[0][3] =~ /,(.+?):([0-9]+)/
					mm.each { mmm ->
						if (mmm[2] =~ /"([0-9]+)"/) {
							mmm[2] = (mmm[2] =~ /"([0-9]+)"/)[0][1]
						}
						if (counterMap[mmm[1]] == null) {
							counterMap[mmm[1]] = 0L
						}


						
						printf '%s%20s = %s\n', ' '*30, Long.parseLong(mmm[2]) - counterMap[mmm[1]], mmm[1]
						
						counterMap[mmm[1]] = Long.parseLong(mmm[2])
					}
					break
				case ~/(?m)^.+=.+/ :
					def m = line =~ /(?m)^(.+?)=.+/
					//printf '                    . %s\n', m[0][1]
					break
				default:
				    break
			}
		}
	}
}
