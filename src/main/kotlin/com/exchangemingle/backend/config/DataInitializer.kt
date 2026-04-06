package com.exchangemingle.backend.config

import com.exchangemingle.backend.model.Skill
import com.exchangemingle.backend.repository.SkillRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DataInitializer(
    private val skillRepository: SkillRepository
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(DataInitializer::class.java)

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (skillRepository.count() > 0) {
            log.info("Skills already seeded — skipping.")
            return
        }

        val seeds = listOf(
            // Programming & Development
            Triple("Python Programming",       "Programming",        "Learn Python from basics to advanced data science and automation"),
            Triple("JavaScript",               "Programming",        "Frontend and backend development with modern JavaScript"),
            Triple("TypeScript",               "Programming",        "Typed superset of JavaScript for scalable applications"),
            Triple("Java",                     "Programming",        "Object-oriented programming and enterprise application development"),
            Triple("Kotlin",                   "Programming",        "Modern JVM language for Android and backend development"),
            Triple("C++",                      "Programming",        "Systems programming and high-performance application development"),
            Triple("Rust",                     "Programming",        "Memory-safe systems programming language"),
            Triple("Go (Golang)",              "Programming",        "Fast, statically typed language for cloud and backend services"),
            Triple("Swift",                    "Programming",        "Apple's language for iOS and macOS app development"),
            Triple("C# (.NET)",                "Programming",        "Microsoft ecosystem development with .NET framework"),
            Triple("Ruby on Rails",            "Programming",        "Rapid web application development with Ruby"),
            Triple("PHP",                      "Programming",        "Server-side web development and CMS customization"),
            Triple("R Programming",            "Programming",        "Statistical computing and data analysis"),

            // Web Development
            Triple("React.js",                 "Web Development",    "Building interactive UIs with Facebook's React library"),
            Triple("Next.js",                  "Web Development",    "Full-stack React framework with SSR and SSG"),
            Triple("Vue.js",                   "Web Development",    "Progressive JavaScript framework for web interfaces"),
            Triple("Angular",                  "Web Development",    "TypeScript-based frontend framework by Google"),
            Triple("Node.js",                  "Web Development",    "Server-side JavaScript runtime and REST API development"),
            Triple("HTML & CSS",               "Web Development",    "Web page structure and styling fundamentals"),
            Triple("Tailwind CSS",             "Web Development",    "Utility-first CSS framework for rapid UI development"),
            Triple("Spring Boot",              "Web Development",    "Java-based framework for production-ready microservices"),
            Triple("Django",                   "Web Development",    "High-level Python web framework for rapid development"),
            Triple("FastAPI",                  "Web Development",    "Modern Python web framework for building APIs"),

            // Mobile Development
            Triple("Android Development",      "Mobile",             "Native Android app development with Kotlin and Jetpack Compose"),
            Triple("iOS Development",          "Mobile",             "Native iPhone and iPad app development with Swift"),
            Triple("Flutter",                  "Mobile",             "Google's cross-platform UI toolkit for mobile and web"),
            Triple("React Native",             "Mobile",             "Cross-platform mobile development with React"),

            // Data & AI
            Triple("Machine Learning",         "Data & AI",          "Building predictive models and intelligent systems"),
            Triple("Deep Learning",            "Data & AI",          "Neural networks and advanced AI with TensorFlow/PyTorch"),
            Triple("Data Analysis",            "Data & AI",          "Analysing datasets with pandas, NumPy and visualisation tools"),
            Triple("Data Science",             "Data & AI",          "End-to-end data science pipeline and model deployment"),
            Triple("Computer Vision",          "Data & AI",          "Image recognition and processing with OpenCV and deep learning"),
            Triple("Natural Language Processing","Data & AI",         "Text analysis, transformers, and language model fine-tuning"),
            Triple("SQL & Databases",          "Data & AI",          "Relational database design, querying, and optimisation"),

            // Cloud & DevOps
            Triple("AWS",                      "Cloud & DevOps",     "Amazon Web Services cloud architecture and services"),
            Triple("Docker & Kubernetes",      "Cloud & DevOps",     "Containerisation and orchestration for microservices"),
            Triple("DevOps & CI/CD",           "Cloud & DevOps",     "Automating build, test, and deployment pipelines"),
            Triple("Linux & Shell Scripting",  "Cloud & DevOps",     "Command-line mastery and automation with Bash"),
            Triple("Google Cloud Platform",    "Cloud & DevOps",     "GCP services including Compute, BigQuery, and Kubernetes"),
            Triple("Azure",                    "Cloud & DevOps",     "Microsoft Azure cloud platform and services"),

            // Design & Creative
            Triple("UI/UX Design",             "Design",             "User interface design principles and Figma prototyping"),
            Triple("Graphic Design",           "Design",             "Visual communication using Adobe tools and design principles"),
            Triple("Figma",                    "Design",             "Collaborative interface design and prototyping tool"),
            Triple("Video Editing",            "Design",             "Professional video editing with Premiere Pro or DaVinci Resolve"),
            Triple("Motion Graphics",          "Design",             "Animation and visual effects with After Effects"),
            Triple("Photography",              "Design",             "Camera technique, composition, and photo editing"),
            Triple("3D Modelling",             "Design",             "3D art and animation with Blender or Maya"),

            // Business & Finance
            Triple("Digital Marketing",        "Business",           "SEO, social media, paid ads, and growth strategies"),
            Triple("Content Writing",          "Business",           "Blog posts, copywriting, and SEO-optimised content"),
            Triple("Excel & Spreadsheets",     "Business",           "Advanced Excel formulas, pivot tables, and data dashboards"),
            Triple("Product Management",       "Business",           "Agile methodologies, roadmapping, and stakeholder management"),
            Triple("Financial Modelling",      "Business",           "Building financial models and valuation frameworks"),
            Triple("Public Speaking",          "Business",           "Presentation skills, storytelling, and audience engagement"),

            // Languages
            Triple("English Communication",    "Languages",          "Conversational English, grammar, and professional writing"),
            Triple("Spanish",                  "Languages",          "Spanish language from beginner to advanced levels"),
            Triple("French",                   "Languages",          "French language learning for all proficiency levels"),
            Triple("Japanese",                 "Languages",          "Japanese language including Hiragana, Katakana, and Kanji"),
            Triple("Mandarin Chinese",         "Languages",          "Mandarin Chinese language and culture"),
            Triple("German",                   "Languages",          "German language for communication and professional use"),

            // Music & Arts
            Triple("Guitar",                   "Music & Arts",       "Acoustic and electric guitar for beginners to advanced players"),
            Triple("Piano & Keyboard",         "Music & Arts",       "Piano fundamentals, music theory, and performance"),
            Triple("Music Production",         "Music & Arts",       "Beat making, mixing, and mastering with DAW tools"),
            Triple("Drawing & Illustration",   "Music & Arts",       "Sketching, digital illustration, and artistic fundamentals"),

            // Academic
            Triple("Mathematics",              "Academic",           "Algebra, calculus, statistics, and competitive mathematics"),
            Triple("Physics",                  "Academic",           "Classical mechanics, electromagnetism, and modern physics"),
            Triple("Chemistry",                "Academic",           "Organic, inorganic, and physical chemistry"),
            Triple("Biology",                  "Academic",           "Cell biology, genetics, ecology, and exam preparation"),
        )

        val saved = seeds.map { (name, category, description) ->
            skillRepository.save(Skill(name = name, category = category, description = description))
        }

        log.info("Seeded ${saved.size} skills successfully.")
    }
}