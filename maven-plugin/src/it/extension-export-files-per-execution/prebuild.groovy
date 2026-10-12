import name.velikodniy.jcexpress.converter.JavaCardVersion
import name.velikodniy.jcexpress.converter.resolve.GlobalPlatformApi

// Two export files of org.globalplatform, written from the converter's own data at build time (never committed):
// version 1.5 for the 3.0.4 execution, version 1.6 for the 3.0.5 one
['1.5': JavaCardVersion.V3_0_4, '1.6': JavaCardVersion.V3_0_5].each { version, target ->
    def file = new File(basedir, "gp-${version}/globalplatform.exp")
    file.parentFile.mkdirs()
    file.bytes = GlobalPlatformApi.exportFileBytes(version, target)
}
return true
