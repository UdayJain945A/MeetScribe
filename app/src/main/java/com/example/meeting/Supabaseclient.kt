package com.example.meeting

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage


object Supabaseclient {
    var supabase = createSupabaseClient(
        supabaseUrl = "https://ztrvzfvnipeqsrtclsdn.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inp0cnZ6ZnZuaXBlcXNydGNsc2RuIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjAwMDgxNzcsImV4cCI6MjA3NTU4NDE3N30.dBbgq7e1-J7jxXnI-AlWzZ-3aRPvDM263sHCaoMzXvo"
    )
    {
        install(Postgrest)
        install(Storage)
        install(Auth)
    }

    fun getCurrentUserId(): String?{
        return supabase.auth.currentUserOrNull()?.id
    }
    fun getCurrentUserEmail() : String?{
        return supabase.auth.currentUserOrNull()?.email
    }

    fun isUserAuthenticated() : Boolean{
        return supabase.auth.currentUserOrNull() != null
    }

}


